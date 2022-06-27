package com.esri.geoevent.adapter.flextext;

import org.apache.commons.lang3.StringUtils;

import com.esri.core.geometry.SpatialReference;
import com.esri.ges.core.geoevent.FieldException;
import com.esri.ges.core.geoevent.FieldGroup;
import com.esri.ges.framework.i18n.BundleLogger;
import com.esri.ges.framework.i18n.BundleLoggerFactory;
import com.esri.ges.util.Converter;

public class AdapterDefaultSpatialReferenceUtil {
	private static final BundleLogger LOGGER = BundleLoggerFactory.getLogger(AdapterDefaultSpatialReferenceUtil.class);

	public static SpatialReference getSpatialReference(FieldGroup event, SpatialReference defaultSpatialReference,
			String spatialReferenceField) throws FieldException {
		SpatialReference thisSpatialReference = null;
		if (defaultSpatialReference == null) {
			try {
				if (spatialReferenceField.isEmpty())
					thisSpatialReference = SpatialReference.create(4326);
				else {
					Object srObject = event.getField(spatialReferenceField);
					if (srObject != null) {
						if (srObject instanceof Integer)
							thisSpatialReference = SpatialReference.create(Converter.convertToInteger(srObject));
						else if (srObject instanceof String) {
							String srString = Converter.convertToString(srObject);
							if (StringUtils.isNumeric(srString))
								thisSpatialReference = SpatialReference.create(Integer.parseInt(srString));
							else
								thisSpatialReference = SpatialReference.create(srString);
						}
					} else {
						throw new Exception(LOGGER.translate("INBOUND_ERROR_SPATIAL_REFERENCE_FILED_EMPTY"));
					}
				}
			} catch (Exception e) {
				LOGGER.error(e.getMessage());
				throw new FieldException(
						LOGGER.translate("INBOUND_ERROR_CREATING_SPATIAL_REFERENCE_FROM_FIELD", spatialReferenceField));
			}
		} else {
			thisSpatialReference = defaultSpatialReference;
		}
		return thisSpatialReference;
	}
}
