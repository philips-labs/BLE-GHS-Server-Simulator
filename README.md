[![Android CI](https://github.com/philips-internal/bluetooth-server-example/actions/workflows/android.yml/badge.svg)](https://github.com/philips-internal/bluetooth-server-example/actions/workflows/android.yml)

# bluetooth-server-example (working title)

**Description**:  
An implementation of a Generic Health Sensor server on Android.

This codebase implements a Bluetooth server / peripheral on an Android device (as an app on typically a mobile phone) that is capable of transmitting any type of health observation to clients.

The server supports the Generic Health Sensor (GHS) GATT service that is under development in the Bluetooth SIG. This service in turn is based on the IEEE 11073-10206 specification that specifies an Abstract Content Model for personal health device data - covering any type of health observation that can be modeled using the IEEE 11073-10101 nomenclature system. These standards provide a long-desired solution for interoperable personal health devices using Bluetooth Low Energy. Once adopted this will reduce integration efforts of using personal health device device in all kinds of (out-of-hospital) healthcare solutions.

The server works with / come with a client application that uses the "blessed" BLE library. This application includes a service handler that implements the GHS client / collector and a UI that displays some details of a connected GHS server and the observations received from such a server. 
 
**Technology stack**: 
The code is written in Kotlin and implements a standalone Android application. 
The UI part is kept seperate from the GHS server implementation.

**Key concepts**:
This codebase is used as a demonstrator of the GHS specification features and will also be used for Bluetooth SIG Interoperability Testing of the GHS specification.
The Android client application serves the same purpose, but the service handler can be used as part of a product quality Android application.

**Status**:  Alpha - work in progress, in parallel with the specificaion work done in IEEE and the Bluetooth SIG.

Latest updates: link to the [CHANGELOG](CHANGELOG.md).

- **Links to production or demo instances**
  - Describe what sets this apart from related-projects. Linking to another doc or page is OK if this can't be expressed in a sentence or two.

**Screenshot**: If the software has visual components, place a screenshot after the description.

## Usage

Show users how to use the software.
Be specific.
Use appropriate formatting when showing code snippets.

## Known issues

Document any known significant shortcomings with the software.

## Contact / Getting help

For further questions on this project, contact one of:
* Abdul Nabi - lead author - abdul.nabi@philips.com
* Erik Moll - development of the Bluetooth SIG GHS and IEEE ACOM specifications - erik.moll@philips.com
* Martijn van Welie - lead author of the Blessed library - martijn.van.welie@philips.com

## License
This code is available under the MIT license.
