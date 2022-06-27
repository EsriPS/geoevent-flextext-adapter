package com.esri.geoevent.adapter.flextext;

import com.esri.ges.adapter.Adapter;
import com.esri.ges.adapter.AdapterServiceBase;
import com.esri.ges.adapter.util.XmlAdapterDefinition;
import com.esri.ges.core.component.ComponentException;
import com.esri.ges.framework.i18n.BundleLogger;
import com.esri.ges.framework.i18n.BundleLoggerFactory;

public class TextInboundAdapterService extends AdapterServiceBase
{
  private static final BundleLogger LOGGER = BundleLoggerFactory.getLogger(TextInboundAdapterService.class);

  public TextInboundAdapterService()
  {
    XmlAdapterDefinition xmlDefinition = new XmlAdapterDefinition(getResourceAsStream("inboundadapter-definition.xml"));

    try
    {
      xmlDefinition.loadConnector(getResourceAsStream("input-tcp-flextext-connector-definition.xml"));
      xmlDefinition.loadConnector(getResourceAsStream("input-file-flextext-connector-definition.xml"));
    }
    catch (Exception e)
    {
      LOGGER.error("Failed to load connector definitions. You will have to create them manually.", e);
    }

    this.definition = xmlDefinition;

  }

  @Override
  public Adapter createAdapter() throws ComponentException
  {
    return new TextInboundAdapter(definition);
  }
}
