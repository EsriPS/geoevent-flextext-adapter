# GeoEvent Event Flexible Text Adapter

This custom adapter updates the OOTB Text adapter to allow it to read text events with a variable number of fields.

The data is imported into a single definition, so the order of the fields does matter. But if the last three fields are left off, the event is still created, placing a null value into those last three fields. Alternatively, if the data contains more fields than there are in the definition, the additional fields in the data will be ignored.

## Features
* GeoEvent Event Flexible Text Adapter

## Requirements

* ArcGIS GeoEvent Processor for Server version 10.6 or later.
* ArcGIS GeoEvent Processor SDK version 10.6.
* Java JDK 1.8 or greater.
* Maven 3.6.3 or greater.

## Instructions

Building the source code:

1. Make sure Maven and ArcGIS GeoEvent Processor SDK are installed on your machine.  <br>
 _c:\temp>_ javac -version <br>
 _c:\temp>_ mvn -version  <br>
2. Clone the repository to your temp drive  <br>
 _c:\temp>_ git clone <repository URL> CD into the directory  <br>
3. Build with maven  <br>
 _c:\temp>_ mvn clean install -Dcontact.address=[YourContactEmailAddress]'

Installing the built jar files:

1. Use the .jar file built above or download a [zip of jar and documentation](https://www.arcgis.com/home/item.html?id=cf02f3b8564042db8de60f582e1ad2a3).
2. Copy the jar files into the [ArcGIS-GeoEvent-Processor-Install-Directory]/deploy folder.

## Resources

* [ArcGIS GeoEvent Server SDK](https://enterprise.arcgis.com/en/geoevent/latest/reference/getting-started-with-the-geoevent-server-sdk.htm)
* [ArcGIS GeoEvent Server](https://enterprise.arcgis.com/en/geoevent/)
* [Esri GeoEvent Community](https://enterprise.arcgis.com/en/geoevent/latest/reference/getting-started-with-the-geoevent-server-sdk.htm)

## Issues

Find a bug or want to request a new feature?  Please let us know by submitting an issue.

## Support

This component is not officially supported as an Esri product. The source code is available under the Apache License. 

## Contributing

Esri welcomes contributions from anyone and everyone. Please see our [guidelines for contributing](https://github.com/esri/contributing).

## Usage

The following parameters are supported:

* `Remove Null Values` Yes, will remove any null json objects from the outgoing json structure.


## Licensing
Copyright 2013 Esri

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

A copy of the license is available in the repository's [license.txt](https://github.com/EsriPS/geoevent-flextext-adapter/blob/master/LICENSE) file.
