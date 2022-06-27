package com.esri.geoevent.adapter.flextext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import com.esri.core.geometry.MapGeometry;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.SpatialReference;
import com.esri.ges.adapter.AdapterDefinition;
import com.esri.ges.adapter.InboundAdapterBase;
import com.esri.ges.core.AccessType;
import com.esri.ges.core.ConfigurationException;
import com.esri.ges.core.Uri;
import com.esri.ges.core.component.ComponentException;
import com.esri.ges.core.geoevent.DefaultFieldDefinition;
import com.esri.ges.core.geoevent.DefaultGeoEventDefinition;
import com.esri.ges.core.geoevent.FieldDefinition;
import com.esri.ges.core.geoevent.FieldException;
import com.esri.ges.core.geoevent.FieldType;
import com.esri.ges.core.geoevent.GeoEvent;
import com.esri.ges.core.geoevent.GeoEventDefinition;
import com.esri.ges.core.i18n.NumberUtil;
import com.esri.ges.framework.i18n.BundleLogger;
import com.esri.ges.framework.i18n.BundleLoggerFactory;
import com.esri.ges.manager.geoeventdefinition.GeoEventDefinitionManagerException;
import com.esri.ges.messaging.MessagingException;
import com.esri.ges.util.Converter;
import com.esri.ges.util.DateFormatID;
import com.esri.ges.util.GeometryUtil;
import com.esri.ges.util.Validator;

public class TextInboundAdapter extends InboundAdapterBase implements TextAdapterProvider
{
  private static final BundleLogger LOGGER                                                   = BundleLoggerFactory.getLogger(TextInboundAdapter.class);

  public static final String        ATTRIBUTE_SEPARATOR_PROPERTY_NAME                        = "AttributeSeparator";
  public static final String        MESSAGE_SEPARATOR_PROPERTY_NAME                          = "MessageSeparator";
  public static final String        CREATE_UNRECOGNIZED_MESSAGE_DEFINITIONS_PROPERTY_NAME    = "CreateUnrecognizedGeoEventDefinitions";
  public static final String        INCOMING_DATA_CONTAINS_GEOEVENT_DEFINITION_PROPERTY_NAME = "IncomingDataContainsGeoEventDefinition";
  public static final String        CREATE_FIXED_GEOEVENT_DEFINITION_PROPERTY_NAME           = "CreateFixedGeoEventDefinitions";
  public static final String        NEW_FIXED_GEOEVENT_DEFINITION_NAME_PROPERTY_NAME         = "NewFixedGeoEventDefinitionName";
  public static final String        EXISTING_FIXED_GEOEVENT_DEFINITION_NAME_PROPERTY_NAME    = "ExistingFixedGeoEventDefinitionName";
  public static final String        BUILD_GEOMETRY_FROM_FIELDS_PROPERTY_NAME                 = "BuildGeometryFromFields";
  public static final String        X_GEOMETRY_FIELD_PROPERTY_NAME                           = "XGeometryField";
  public static final String        Y_GEOMETRY_FIELD_PROPERTY_NAME                           = "YGeometryField";
  public static final String        Z_GEOMETRY_FIELD_PROPERTY_NAME                           = "ZGeometryField";
  public static final String        SPATIAL_REFERENCE_PROPERTY_NAME                          = "SpatialReferenceField";
  private SpatialReference          defaultSpatialReference                                  = null;
  private String                    spatialReferenceField                                    = "";
  public static final String        CUSTOM_DATE_FORMAT_PROPERTY_NAME                         = "CustomDateFormat";
  public static final String        LOCALE_FOR_NUMBER_FORMATTER                              = "LocaleForNumberFormatter";
  // private static final String CR = "\r";
  private static final String       LF                                                       = "\n";
  private static final String       NULL_CHANNEL_ID                                          = "NullChannelID";
  private static final String       GEOMETRY_TAG_NAME                                        = "GEOMETRY";

  private String                    messageSeparator                                         = LF;
  private char                      attributeSeparator                                       = ',';
  private boolean                   creatingUnrecognizedGeoEventDefinitions                  = false;
  private boolean                   multipleGeoEventDefinitions                              = true;
  private String                    geoEventDefinitionName                                   = null;
  private Pattern                   messageSeparatorPattern                                  = null;

  private boolean                   buildGeometryFromFields;
  private String                    xGeometryField;
  private String                    yGeometryField;
  private String                    zGeometryField;

  private String                    customDateFormat                                         = "'";
  private DateFormatID              dateParserID                                             = new DateFormatID(-1);
  private Map<String, String>       leftOversByChannelId                                     = new ConcurrentHashMap<String, String>();

  private String                    localeString                                             = "";
  // private NumberUtil numberUtil = NumberUtil.getInstance();

  public TextInboundAdapter(AdapterDefinition adapterDefinition) throws ComponentException
  {
    super(adapterDefinition);
  }

  @Override
  public void afterPropertiesSet()
  {
    if (hasProperty(MESSAGE_SEPARATOR_PROPERTY_NAME))
    {
      messageSeparator = getProperty(MESSAGE_SEPARATOR_PROPERTY_NAME).getValueAsString();
      if (messageSeparator.equals(""))
        messageSeparator = LF;
      messageSeparator = TextUtil.unescape(messageSeparator);
      messageSeparatorPattern = Pattern.compile(messageSeparator);
    }
    if (hasProperty(CREATE_UNRECOGNIZED_MESSAGE_DEFINITIONS_PROPERTY_NAME))
      creatingUnrecognizedGeoEventDefinitions = ((Boolean) (getProperty(CREATE_UNRECOGNIZED_MESSAGE_DEFINITIONS_PROPERTY_NAME).getValue())).booleanValue();
    if (hasProperty(INCOMING_DATA_CONTAINS_GEOEVENT_DEFINITION_PROPERTY_NAME))
      multipleGeoEventDefinitions = ((Boolean) (getProperty(INCOMING_DATA_CONTAINS_GEOEVENT_DEFINITION_PROPERTY_NAME).getValue())).booleanValue();
    if (!multipleGeoEventDefinitions && hasProperty(CREATE_FIXED_GEOEVENT_DEFINITION_PROPERTY_NAME))
      creatingUnrecognizedGeoEventDefinitions = ((Boolean) (getProperty(CREATE_FIXED_GEOEVENT_DEFINITION_PROPERTY_NAME).getValue())).booleanValue();
    if (!multipleGeoEventDefinitions && creatingUnrecognizedGeoEventDefinitions && hasProperty(NEW_FIXED_GEOEVENT_DEFINITION_NAME_PROPERTY_NAME))
    {
      geoEventDefinitionName = getProperty(NEW_FIXED_GEOEVENT_DEFINITION_NAME_PROPERTY_NAME).getValueAsString();
    }
    if (!multipleGeoEventDefinitions && !creatingUnrecognizedGeoEventDefinitions && hasProperty(EXISTING_FIXED_GEOEVENT_DEFINITION_NAME_PROPERTY_NAME))
    {
      geoEventDefinitionName = getProperty(EXISTING_FIXED_GEOEVENT_DEFINITION_NAME_PROPERTY_NAME).getValueAsString();
    }

    if (hasProperty(BUILD_GEOMETRY_FROM_FIELDS_PROPERTY_NAME))
    {
      buildGeometryFromFields = ((Boolean) (getProperty(BUILD_GEOMETRY_FROM_FIELDS_PROPERTY_NAME).getValue())).booleanValue();
      if (buildGeometryFromFields)
      {
        if (hasProperty(X_GEOMETRY_FIELD_PROPERTY_NAME))
        {
          xGeometryField = getProperty(X_GEOMETRY_FIELD_PROPERTY_NAME).getValueAsString();
        }
        if (hasProperty(Y_GEOMETRY_FIELD_PROPERTY_NAME))
        {
          yGeometryField = getProperty(Y_GEOMETRY_FIELD_PROPERTY_NAME).getValueAsString();
        }
        if (hasProperty(Z_GEOMETRY_FIELD_PROPERTY_NAME))
        {
          zGeometryField = getProperty(Z_GEOMETRY_FIELD_PROPERTY_NAME).getValueAsString();
        }
      }
    }
    else
      buildGeometryFromFields = false;

    if (hasProperty(SPATIAL_REFERENCE_PROPERTY_NAME))
    {
      String value = getProperty(SPATIAL_REFERENCE_PROPERTY_NAME).getValueAsString();
      try
      {
        if (StringUtils.isNumeric(value))
        {
          defaultSpatialReference = SpatialReference.create(Integer.parseInt(value));
        }
        else if (value.contains("\"")) // must be wkt
        {
          defaultSpatialReference = SpatialReference.create(value);
        }
        else // must be field or empty
        {
          defaultSpatialReference = null;
          spatialReferenceField = value;
        }
      }
      catch (Exception e)
      {
        LOGGER.error("INBOUND_ERROR_CREATING_SPATIAL_REFERENCE", value, e);
      }
    }

    if (hasProperty(CUSTOM_DATE_FORMAT_PROPERTY_NAME))
    {
      customDateFormat = getProperty(CUSTOM_DATE_FORMAT_PROPERTY_NAME).getValueAsString();
    }

    if (hasProperty(ATTRIBUTE_SEPARATOR_PROPERTY_NAME))
    {
      String attributeSeparatorString = getProperty(ATTRIBUTE_SEPARATOR_PROPERTY_NAME).getValueAsString();
      attributeSeparatorString = TextUtil.unescape(attributeSeparatorString);
      attributeSeparator = attributeSeparatorString.charAt(0);
    }
    if (hasProperty(LOCALE_FOR_NUMBER_FORMATTER))
    {
      // String localeString =
      // getProperty(LOCALE_FOR_NUMBER_FORMATTER).getValueAsString();
      // numberUtil = NumberUtil.getInstance(localeString);
      localeString = getProperty(LOCALE_FOR_NUMBER_FORMATTER).getValueAsString();
    }
  }

  private void addGeometryTag(FieldDefinition geometryField, List<FieldDefinition> fieldDefinitions)
  {
    removeGeometryTag(fieldDefinitions);
    geometryField.addTag(GEOMETRY_TAG_NAME);
  }

  private void removeGeometryTag(List<FieldDefinition> fieldDefinitions)
  {
    for (FieldDefinition fd : fieldDefinitions)
    {
      if (fd.getTags().contains(GEOMETRY_TAG_NAME))
      {
        fd.removeTag(GEOMETRY_TAG_NAME);
      }
    }
  }

  private FieldDefinition getGeometryFieldDefinition(List<FieldDefinition> fieldDefinitions)
  {
    for (FieldDefinition fd : fieldDefinitions)
      if (fd.hasTag(GEOMETRY_TAG_NAME))
        return fd;
    return null;
  }

  private GeoEvent translate(String message)
  {
    if (LOGGER.isTraceEnabled())
      LOGGER.trace(message);
    NumberUtil numberUtil = null;
    if (localeString.isEmpty())
      numberUtil = NumberUtil.getInstance();
    else
      numberUtil = NumberUtil.getInstance(localeString);
    GeoEvent geoEvent = null;
    if (message != null && message.trim().length() > 0)
    {
      String fieldName = null;
      String lastLine = null;
      try
      {
        // CSVParser parser = new CSVParserBuilder().withSeparator(attributeSeparator).build();
        lastLine = message.trim();

        String[] elems = lastLine.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
        if (LOGGER.isTraceEnabled())
          LOGGER.trace("Split line with {0} elements to : {1}", Arrays.toString(elems));
        for (int elemsIndex = 0; elemsIndex < elems.length; elemsIndex++)
        {
          elems[elemsIndex] = elems[elemsIndex].replace("\"", "");
        }
        if (LOGGER.isTraceEnabled())
          LOGGER.trace("Split line omitting quote characters with {0} elements to : {1}", Arrays.toString(elems));

        // parser.parseLine(message.trim());
        if (elems.length > 0)
        {
          String mdName = null;
          if (!multipleGeoEventDefinitions)
            mdName = geoEventDefinitionName;
          else
          {
            mdName = elems[0];
            String[] attributesOnly = new String[elems.length - 1];
            System.arraycopy(elems, 1, attributesOnly, 0, attributesOnly.length);
            elems = attributesOnly;
          }
          LOGGER.trace("Looking for def: {0}", mdName);
          Collection<GeoEventDefinition> definitions = geoEventCreator.getGeoEventDefinitionManager().searchGeoEventDefinitionByName(mdName);
          if (LOGGER.isTraceEnabled())
            LOGGER.trace("Search resulted in the following defs: {0}", Arrays.toString(definitions.toArray()));
          GeoEventDefinition def = null;
          if (definitions == null || definitions.size() == 0)
          {
            LOGGER.trace("Didn't find a def.");
            if (creatingUnrecognizedGeoEventDefinitions)
            {
              LOGGER.trace("Trying to create a new def");
              try
              {
                def = deriveGeoEventDefinition(mdName, elems);
                if (def != null)
                {
                  Uri uri = new Uri("auto-generated", definition.getDomain() + "." + definition.getName(), definition.getVersion());
                  def.setOwner(uri.toString());

                  geoEventCreator.getGeoEventDefinitionManager().addGeoEventDefinition(def);
                  LOGGER.debug("TEXT_PARSING_AUTO_CREATE_GED", mdName, message);
                }
              }
              catch (GeoEventDefinitionManagerException e)
              {
                LOGGER.error("FAILED_TO_ADD_GED_ERROR", message, e.getMessage());
                LOGGER.info(e.getMessage(), e);
                return null;
              }
              catch (ConfigurationException e)
              {
                LOGGER.error("FAILED_TO_DERIVE_GED_ERROR", message, e.getMessage());
                LOGGER.info(e.getMessage(), e);
                return null;
              }
            }
            else
            {
              LOGGER.error("FAILED_TO_TRANSLATE_MSG_ERROR", mdName);
              return null;
            }
          }
          else
          {
            int derivableFieldCount = elems.length;
            LOGGER.trace("Text has {0} fields", derivableFieldCount);
            // if (elems.length > 0 && buildGeometryFromFields)
            // derivableFieldCount++;
            for (GeoEventDefinition md : definitions)
            {
              if (LOGGER.isTraceEnabled())
                LOGGER.trace("Evaluating definition for match: {0}", md.getName());
              List<FieldDefinition> fieldDefinitions = md.getFieldDefinitions();

              if (fieldDefinitions == null && derivableFieldCount == 0)
                continue;

              if (buildGeometryFromFields)
              {
                LOGGER.trace("Building geometry from fields. Checking if def has geometry");
                FieldDefinition geometryFieldDefinition = getGeometryFieldDefinition(fieldDefinitions);
                LOGGER.warn("geometry field is {0}", geometryFieldDefinition);
                if (geometryFieldDefinition == null)
                {
                  LOGGER.warn("Building geometry from fields. Didn't find a geometry field, so adding it.");
                  // if (fieldDefinitions.size() <= derivableFieldCount
                  // && creatingUnrecognizedGeoEventDefinitions) {
                  if (creatingUnrecognizedGeoEventDefinitions)
                  {
                    // Add geometry field to GED

                    removeGeometryTag(fieldDefinitions);
                    try
                    {
                      GeoEventDefinition newGED = md.augment(Arrays.asList(new DefaultFieldDefinition("Geometry", FieldType.Geometry, "GEOMETRY")));
                      md.setFieldDefinitions(newGED.getFieldDefinitions());
                      geoEventCreator.getGeoEventDefinitionManager().updateGeoEventDefinition(md);
                      LOGGER.trace("Building geometry from fields. Updated definition so it has a geometry field.");
                      def = md;
                      break;
                    }
                    catch (Exception e)
                    {
                      LOGGER.error("FAILED_TO_ADD_GEOMETRY_FIELD_ERROR", e);
                      return null;
                    }
                  }
                }
                else
                {
                  LOGGER.trace("Building geometry from fields. Found a geometry field. checking if it has a Geometry tag.");
                  // don't care if the number of fields match, we're flexible
                  // if (fieldDefinitions.size() == derivableFieldCount
                  // || fieldDefinitions.size() == derivableFieldCount + 1) {
                  try
                  {
                    if (!geometryFieldDefinition.hasTag(GEOMETRY_TAG_NAME))
                    {
                      addGeometryTag(geometryFieldDefinition, fieldDefinitions);
                      md.setFieldDefinitions(md.getFieldDefinitions());
                      geoEventCreator.getGeoEventDefinitionManager().updateGeoEventDefinition(md);
                    }
                    def = md;
                    break;
                  }
                  catch (Exception e)
                  {
                    LOGGER.error("FAILED_TO_ADD_GEOMETRY_TAG_ERROR", e);
                    return null;
                  }
                  // }
                }
              }
              else
              {
                // if (fieldDefinitions.size() == derivableFieldCount) {
                // don't care, we're flxible
                def = md;
                break;
                // }
              }
            }
            if (def == null)
            {
              LOGGER.warn("CANNOT_FIND_MATCHING_GED", message);
              return null;
            }
            else if (LOGGER.isTraceEnabled())
            {
              LOGGER.debug("Using geoevent definition {0} owned by {1} with guid {2}", def.getName(), def.getOwner(), def.getGuid());
            }
          }
          try
          {
            geoEvent = geoEventCreator.create(def.getGuid());
          }
          catch (MessagingException e)
          {
            LOGGER.error("FAILED_TO_TRANSLATE_MSG_ERROR", def.getName());
          }
          if (geoEvent != null)
          {
            List<? extends FieldDefinition> ads = def.getFieldDefinitions();
            int elementIndex = 0;
            int geometryFieldIndex = -1;
            if (LOGGER.isDebugEnabled())
              LOGGER.debug("Populating geoevent {0} that has {1} fields with text message that has {2} fields", def.getName(), ads.size(), elems.length);
            for (int i = 0; i < ads.size(); i++)
            {
              FieldDefinition fieldDefinition = ads.get(i);
              fieldName = fieldDefinition.getName();
              String s = null;
              if (elementIndex < elems.length)
                s = elems[elementIndex++].trim();
              LOGGER.trace("Adding message element {0}: {1}", elementIndex, s);
              switch (fieldDefinition.getType())
              {
                case String:
                  if (s != null)
                    geoEvent.setField(i, s);
                  else
                    LOGGER.trace("Skipping string field {0} with index {1}, value is null or not present in text message.", fieldName, i);
                  break;
                case Date:
                  if (s != null)
                  {
                    if (s.length() == 0)
                      geoEvent.setField(i, null);
                    else
                    {
                      if (StringUtils.isNumeric(s))
                        geoEvent.setField(i, DateUtilWrapper.convert((long) Double.parseDouble(s)));
                      else
                        geoEvent.setField(i, DateUtilWrapper.convert(s, customDateFormat, dateParserID));
                    }
                  }
                  else
                  {
                    LOGGER.trace("Skipping date field {0} with index {1}, value is null or not present in text message.", fieldName, i);
                  }
                  break;
                case Double:
                  if (s != null)
                  {
                    if (s.length() == 0)
                      geoEvent.setField(i, null);
                    else
                    {
                      try
                      {
                        geoEvent.setField(i, numberUtil.parseDouble(s));
                      }
                      catch (Throwable ex)
                      {
                        LOGGER.error("FLOAT_PARSE_ERROR", s);
                        LOGGER.info(ex.getMessage(), ex);
                        return null;
                      }
                    }
                  }
                  else
                  {
                    LOGGER.trace("Skipping double field {0} with index {1}, value is null or not present in text message.", fieldName, i);
                  }
                  break;
                case Float:
                  if (s != null)
                  {
                    if (s.length() == 0)
                      geoEvent.setField(i, null);
                    else
                    {
                      try
                      {
                        geoEvent.setField(i, numberUtil.parseFloat(s));
                      }
                      catch (Throwable ex)
                      {
                        LOGGER.error("FLOAT_PARSE_ERROR", s);
                        LOGGER.info(ex.getMessage(), ex);
                        return null;
                      }
                    }
                  }
                  else
                  {
                    LOGGER.trace("Skipping float field {0} with index {1}, value is null or not present in text message.", fieldName, i);
                  }
                  break;
                case Geometry:
                  if (buildGeometryFromFields)
                    elementIndex--;
                  else
                  {
                    geometryFieldIndex = i;
                    if (s != null)
                    {
                      try
                      {

                        MapGeometry geom = GeometryUtil.fromJson(s);
                        if (geom != null)
                        {
                          geoEvent.setField(i, geom);
                          break;
                        }
                      }
                      catch (Exception ex)
                      {
                        // Didn't work, try another method. . . .
                      }
                      Point point = null;

                      String[] parts = s.split("" + attributeSeparator);
                      try
                      {
                        if (parts.length > 1)
                        {
                          double x = numberUtil.parseDouble(parts[0], Double.NaN);
                          double y = numberUtil.parseDouble(parts[1], Double.NaN);
                          double z = Double.NaN;
                          if (parts.length > 2)
                            z = numberUtil.parseDouble(parts[2], Double.NaN);
                          int wkid = -1;
                          if (parts.length > 3)
                            wkid = numberUtil.parseInteger(parts[3], 4326);
                          if (!Double.isNaN(z))
                            point = new Point(x, y, z);
                          else
                            point = new Point(x, y);

                          MapGeometry mapGeometry = new MapGeometry(point, wkid > 0 ? SpatialReference.create(wkid) : null);
                          geoEvent.setField(i, mapGeometry);
                        }
                      }
                      catch (Throwable ex)
                      {
                        LOGGER.error("CSV_PARSE_ERROR", ex.getMessage());
                        return null;
                      }
                    }
                    else
                    {
                      LOGGER.trace("Skipping geometry field {0} with index {1}, value is null or not present in text message.", fieldName, i);
                    }
                  }

                  break;
                case Integer:
                  if (s != null)
                  {
                    if (s.length() == 0)
                      geoEvent.setField(i, null);
                    else
                    {
                      try
                      {
                        geoEvent.setField(i, numberUtil.parseInteger(s));
                      }
                      catch (Throwable ex)
                      {
                        LOGGER.error("INT_PARSE_ERROR", s);
                        LOGGER.info(ex.getMessage(), ex);
                        return null;
                      }
                    }
                  }
                  else
                  {
                    LOGGER.trace("Skipping integer field {0} with index {1}, value is null or not present in text message.", fieldName, i);
                  }
                  break;
                case Long:
                  if (s != null)
                  {
                    if (s.length() == 0)
                      geoEvent.setField(i, null);
                    else
                    {
                      try
                      {
                        geoEvent.setField(i, numberUtil.parseLong(s));
                      }
                      catch (Throwable ex)
                      {
                        LOGGER.error("INT_PARSE_ERROR", s);
                        LOGGER.info(ex.getMessage(), ex);
                        return null;
                      }
                    }
                  }
                  else
                  {
                    LOGGER.trace("Skipping long field {0} with index {1}, value is null or not present in text message.", fieldName, i);
                  }
                  break;
                case Short:
                  if (s != null)
                  {
                    if (s.length() == 0)
                      geoEvent.setField(i, null);
                    else
                    {
                      try
                      {
                        geoEvent.setField(i, numberUtil.parseShort(s));
                      }
                      catch (Throwable ex)
                      {
                        LOGGER.error("INT_PARSE_ERROR", s);
                        LOGGER.info(ex.getMessage(), ex);
                        return null;
                      }
                    }
                  }
                  else
                  {
                    LOGGER.trace("Skipping short field {0} with index {1}, value is null or not present in text message.", fieldName, i);
                  }
                  break;
                case Boolean:
                  if (s != null)
                  {
                    if (s.length() == 0)
                      geoEvent.setField(i, null);
                    else
                      geoEvent.setField(i, BooleanUtils.toBoolean(s));
                  }
                  else
                  {
                    LOGGER.trace("Skipping boolean field {0} with index {1}, value is null or not present in text message.", fieldName, i);
                  }
                  break;
                case Group:
                  LOGGER.trace("Skipping group field {0} with index {1}, value is null or not present in text message.", fieldName, i);
                  // CSV cannot accurately represent groups.
                  break;
                default:
                  break;
              }
            }
            // We need to check spatial reference after all fields are in geoEvent.
            // Otherwise, we may not get the field value
            if (geometryFieldIndex >= 0)
            {
              Object obj = geoEvent.getField(geometryFieldIndex);
              if (obj instanceof MapGeometry)
              {
                MapGeometry mapGeometry = (MapGeometry) obj;
                if (mapGeometry.getSpatialReference() == null)
                {
                  SpatialReference spatialReference = AdapterDefaultSpatialReferenceUtil.getSpatialReference(geoEvent, defaultSpatialReference, spatialReferenceField);
                  mapGeometry.setSpatialReference(spatialReference);
                  geoEvent.setField(geometryFieldIndex, mapGeometry);
                }
              }
            }
            if (buildGeometryFromFields)
            {
              if (Validator.isNotBlank(xGeometryField) && Validator.isNotBlank(yGeometryField))
              {
                double x = Double.NaN;
                double y = Double.NaN;
                double z = Double.NaN;
                SpatialReference spatialReference = null;

                // parse the x value
                FieldDefinition xFieldDef = def.getFieldDefinition(xGeometryField);
                Object xObject = geoEvent.getField(xGeometryField);
                if (xFieldDef != null && xObject != null)
                {
                  switch (xFieldDef.getType())
                  {
                    case Double:
                      x = (Double) xObject;
                      break;
                    case Integer:
                      x = Double.valueOf(((Integer) xObject).doubleValue());
                      break;
                    case Long:
                      x = Double.valueOf(((Long) xObject).doubleValue());
                      break;
                    case Float:
                      x = Double.valueOf(((Float) xObject).doubleValue());
                      break;
                    case Short:
                      x = Double.valueOf(((Short) xObject).doubleValue());
                      break;
                    case String:
                      x = numberUtil.parseDouble(xObject.toString(), Double.NaN);
                      break;
                    default:
                      x = Converter.convertToDouble(xObject);
                      break;
                  }
                }

                // parse the y value
                FieldDefinition yFieldDef = def.getFieldDefinition(yGeometryField);
                Object yObject = geoEvent.getField(yGeometryField);
                if (yFieldDef != null && yObject != null)
                {
                  switch (yFieldDef.getType())
                  {
                    case Double:
                      y = (Double) yObject;
                      break;
                    case Integer:
                      y = Double.valueOf(((Integer) yObject).doubleValue());
                      break;
                    case Long:
                      y = Double.valueOf(((Long) yObject).doubleValue());
                      break;
                    case Float:
                      y = Double.valueOf(((Float) yObject).doubleValue());
                      break;
                    case Short:
                      y = Double.valueOf(((Short) yObject).doubleValue());
                      break;
                    case String:
                      y = numberUtil.parseDouble(yObject.toString(), Double.NaN);
                      break;
                    default:
                      y = Converter.convertToDouble(yObject);
                      break;
                  }
                }

                if (Validator.isNotBlank(zGeometryField))
                {
                  FieldDefinition zFieldDef = def.getFieldDefinition(zGeometryField);
                  Object zObject = geoEvent.getField(zGeometryField);
                  if (zFieldDef != null && zObject != null)
                  {
                    switch (zFieldDef.getType())
                    {
                      case Double:
                        z = (Double) zObject;
                        break;
                      case Integer:
                        z = Double.valueOf(((Integer) zObject).doubleValue());
                        break;
                      case Long:
                        z = Double.valueOf(((Long) zObject).doubleValue());
                        break;
                      case Float:
                        z = Double.valueOf(((Float) zObject).doubleValue());
                        break;
                      case Short:
                        z = Double.valueOf(((Short) zObject).doubleValue());
                        break;
                      case String:
                        z = numberUtil.parseDouble(zObject.toString(), Double.NaN);
                        break;
                      default:
                        z = Converter.convertToDouble(zObject);
                        break;
                    }
                  }
                }

                spatialReference = AdapterDefaultSpatialReferenceUtil.getSpatialReference(geoEvent, defaultSpatialReference, spatialReferenceField);

                if (spatialReference == null)
                  throw new FieldException(LOGGER.translate("INBOUND_ERROR_CREATING_SPATIAL_REFERENCE_FROM_FIELD", spatialReferenceField));

                Point point = null;
                MapGeometry mapPoint = null;
                if (!Double.isNaN(x) && !Double.isNaN(y))
                {
                  if (!Double.isNaN(z))
                    point = new Point(x, y, z);
                  else
                    point = new Point(x, y);
                  mapPoint = new MapGeometry(point, spatialReference);
                }

                geoEvent.setGeometry(mapPoint);
              }
            }
          }
          else
          {
            LOGGER.error("FAILED_TO_TRANSLATE_MSG_ERROR", def.getName());
          }
        }
      }
      // catch (IOException e) {
      // LOGGER.error("CSV_PARSE_ERROR", lastLine);
      // LOGGER.info(e.getMessage() + " : " + lastLine, e);
      // }
      catch (FieldException e)
      {
        LOGGER.error("FIELD_ERROR", fieldName, e.getMessage());
        LOGGER.info(e.getMessage(), e);
      }
      catch (ParseException e)
      {
        LOGGER.error("FIELD_ERROR", fieldName, e.getMessage());
        LOGGER.info(e.getMessage(), e);
      }
    }
    LOGGER.trace("Returning geoevent: {0}", geoEvent);
    return geoEvent;
  }

  private GeoEventDefinition deriveGeoEventDefinition(String mdName, String[] elems) throws ConfigurationException
  {
    GeoEventDefinition geoEventDefinition = new DefaultGeoEventDefinition();
    geoEventDefinition.setName(mdName);
    geoEventDefinition.setAccessType(AccessType.editable);
    List<FieldDefinition> fieldDefinitions = new ArrayList<FieldDefinition>();
    boolean haveDate = false;
    boolean haveGeometry = false;
    for (int i = 0; i < elems.length; i++)
    {
      String value = elems[i];
      if (looksLikeADate(value))
      {
        if (haveDate)
          fieldDefinitions.add(new DefaultFieldDefinition("Field" + (i + 1), FieldType.Date));
        else
          fieldDefinitions.add(new DefaultFieldDefinition("StartTime", FieldType.Date, "TIME_START"));
        haveDate = true;
        continue;
      }
      if (looksLikeAGeometry(value))
      {
        if (haveGeometry)
          fieldDefinitions.add(new DefaultFieldDefinition("Field" + (i + 1), FieldType.Geometry));
        else
          fieldDefinitions.add(new DefaultFieldDefinition("Geometry", FieldType.Geometry, "GEOMETRY"));
        haveGeometry = true;
        continue;
      }

      fieldDefinitions.add(new DefaultFieldDefinition("Field" + (i + 1), FieldType.String));
    }
    if (buildGeometryFromFields)
    {
      if (!haveGeometry)
        fieldDefinitions.add(new DefaultFieldDefinition("Geometry", FieldType.Geometry, "GEOMETRY"));
    }
    geoEventDefinition.setFieldDefinitions(fieldDefinitions);
    return geoEventDefinition;
  }

  private boolean looksLikeAGeometry(String value)
  {
    String[] parts = value.split("" + attributeSeparator);
    if (parts.length >= 2 && parts.length <= 3)
    {
      boolean success = true;
      success = success && (Converter.convertToDouble(parts[0]) != null);
      success = success && (Converter.convertToDouble(parts[1]) != null);
      if (parts.length > 2)
        success = success && (Converter.convertToDouble(parts[2]) != null);
      if (success)
        return true;
    }
    if (value.contains("{"))
    {
      try
      {
        MapGeometry geom = GeometryUtil.fromJson(value);
        if (geom.getGeometry() != null)
          return true;
      }
      catch (IOException e)
      {
      }
    }
    return false;
  }

  private boolean looksLikeADate(String value)
  {
    if (customDateFormat.isEmpty())
    {
      if (value.contains(":"))
      {
        if (Pattern.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*", value))
          return true;
        if (Pattern.matches("\\d{1,2}/\\d{1,2}/\\d{2,4} \\d{1,2}:\\d{2}:\\d{2}( [a,A,p,P][m,M])?", value))
          return true;
      }
    }
    else
    {
      SimpleDateFormat sdf = new SimpleDateFormat(customDateFormat);
      try
      {
        sdf.parse(value);
        return true;
      }
      catch (ParseException e)
      {
        return false;
      }
    }
    return false;
  }

  @Override
  public void receive(ByteBuffer buffer, String channelId)
  {
    String decodedBuffer = null;
    try
    {
      CharsetDecoder decoder = getCharsetDecoder();
      CharBuffer charBuffer = decoder.decode(buffer);
      decodedBuffer = charBuffer.toString();
    }
    catch (CharacterCodingException e)
    {
      LOGGER.error("CHAR_ENCODING_ERROR");
      LOGGER.info(e.getMessage(), e);
      buffer.position(buffer.limit());
      return;
    }

    try
    {
      // fetch the leftovers by channel id
      String channelIdToStore = channelId;
      if (channelIdToStore == null)
        channelIdToStore = NULL_CHANNEL_ID;

      // check for leftovers from previous message
      String leftOvers = null;
      if (leftOversByChannelId.containsKey(channelIdToStore))
      {
        leftOvers = leftOversByChannelId.remove(channelIdToStore);
        if (StringUtils.isNotEmpty(leftOvers))
        {
          LOGGER.debug("TEXT_PARSING_LEFTOVERS_MSG", leftOvers, decodedBuffer);
          decodedBuffer = leftOvers + decodedBuffer;
        }
      }

      int endOfCurrentMessage = 0;
      int startOfNextMessage = 0;
      Matcher matcher = messageSeparatorPattern.matcher(decodedBuffer);
      boolean hasCompleteMessage = matcher.find();
      while (hasCompleteMessage)
      {
        endOfCurrentMessage = matcher.start();
        String currentMessage = decodedBuffer.substring(startOfNextMessage, endOfCurrentMessage);

        // parse & send
        GeoEvent result = translate(currentMessage);
        if (result != null)
          geoEventListener.receive(result);

        // reset
        startOfNextMessage = matcher.end();
        hasCompleteMessage = matcher.find();
      }

      // check the leftovers
      leftOvers = decodedBuffer.substring(startOfNextMessage);
      if (StringUtils.isNotEmpty(leftOvers))
        leftOversByChannelId.put(channelIdToStore, leftOvers);
    }
    catch (Exception e)
    {
      LOGGER.error("GENERAL_PARSING_ERROR", e.getMessage());
      LOGGER.info(e.getMessage(), e);
    }
  }

  @Override
  public GeoEvent adapt(ByteBuffer buffer, String channelId)
  {
    // We don't need to implement anything in here because this method will never
    // get called. It would normally be
    // called
    // by the base class's receive() method. However, we are overriding that method,
    // and our new implementation does not
    // call
    // the adapter's adapt() method.
    return null;
  }

  @Override
  public String getMessageSeparator()
  {
    return messageSeparator;
  }

}
