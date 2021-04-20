[![Android Build](https://github.com/philips-labs/BLE-GHS-Server-Simulator/actions/workflows/android.yml/badge.svg)](https://github.com/philips-labs/BLE-GHS-Server-Simulator/actions/workflows/android.yml)

# BLE Generic Health Sensor Server Simulator

*Note: It is assumed the reader is familiar with Bluetooth Low Energy GATT Servers and Android development*


**Key concepts**:

This codebase is used as a demonstrator of the GHS specification features and will also be used for Bluetooth SIG Interoperability Testing of the GHS specification. As such it will be contiously modified and extended as the GHS specification evolves.

**Description**:  

An implementation of the proposed Generic Health Sensor standard server for Android that is easily modified or extended to emit various types of health observations.

This BLE Server simulator supports the Generic Health Sensor (GHS) GATT service that is currently under development in the Bluetooth SIG. As it is an evolving specification it can also be used as a "playground" for various BLE properties and data representation.

This service in turn is based on the IEEE 11073-10206 specification that specifies an Abstract Content Model (ACOM) for personal health device data - covering any type of health observation that can be modeled using the IEEE 11073-10101 nomenclature system.

These standards provide a long-sought solution for interoperable personal health devices using Bluetooth Low Energy. When adopted, the GHS GATT service will create both a common BLE and data model, reducing the integration and development efforts of using personal health devices in a broad set of healthcare solutions.

This project implements an example Bluetooth server/peripheral via an Android application that is capable of transmitting any type of specfic health observation to connected clients. In the example heart rate (a simple numeric value), SpO2 (another simple numeric value) and a PPG (array of values). The supported types can be easily extended by the developer.

In addition the application supports "Experimental" modifications to the data format to experiment during the development of the GHS specification.  The expermental features can be easily extended or modified by the developer.

The server works in conjunction with a client that can connect to a GHS service and manage the types of observation data specified. A standard BLE client application (e.g. LightBlue) can be used to connect and view/log the behavior and data from the GHS peripheral.

**Technology stack**: 

The project is written in Kotlin and implements a standalone Android application
The BLE, GHS and data model classes are separated from the Android UX classes for reusability in other GHS server prototypes.

**Status**:

Alpha - work in progress, in parallel with the specificaion work done in IEEE and the Bluetooth SIG.

Latest updates: link to the [CHANGELOG](CHANGELOG.md).

## Project Usage

The project should build and run. Note, the current versions used and tested are:
* Android Studio 4.1.3
* Kotlin 1.4.31

The packages in the project are as follows:
* ```com.philips.btserver.extensions``` - Byte, ByteArray, String, and List extensions that are used in the project (and generally useful)
* ```com.philips.btserver``` - Base classes for BluetoothServer (responsible for overall Server behavior in collaboration with the BluetoothPeripheralManager) and BaseService (the base class for creating service handlers for Device Information, Current Time, and Generic Health Sensor)
* ```com.philips.btserver.gatt``` - Classes that handle the GATT Current Time and Device Information Services.
* ```com.philips.btserver.generichealthservice``` - Classes that handle and suppor the Generic Health Sensor Service, including data models and emitting sample observations.
* ```com.philips.btserverapp``` - Activity, Fragments and Adapter to support the UI.

## Simulator Usage

The Simulator UX consists of a main screen with 3 tabbed pages.

The "Device Info" tab contains properties for the Device Information Service (currently the device name and model number).

The "Observations" tab controls:
* Controlling the types of observations to be sent on each emission.
* Starting and stopping a continous data emitter (with the period defined in the ObservationEmitter class via a emitterPeriod property)
* Emitting a single shot observation(s). 

The "Experimenal" tab has various options being evaluated (but not proposed) for the data format of the emitted observations (note if these are selected any BLE central receiver would need to understand the matching resulting data format). The details of each option are self describing and are not discussed in this document. For those not familiar with the options or terminology it is recommended to not select any of these options.

The developer is free to modify and extend each of these page Fragments and the underlying classes to support other types of devices, observations or data representations.

## Known issues

Given the early state of the Generic Health Sensor (GHS) GATT service within the Bluetooth SIG changes to the code to track the specifcations will be frequent and called out in the [CHANGELOG](CHANGELOG.md).

## Contributing

This project was open sourced primarily to allow for those interested in the Bluetooth General Health Sensor specification to experiment with simulations of devices. We don't anticipate contributions, however, if there is a useful extension, feature, fix or other improvement the project authors and maintainers are eager to merge those into the project code base. More information can be found [here](CONTRIBUTING.md).

## Contact / Getting help

For further questions on this project, contact:
* Abdul Nabi - lead author - abdul.nabi@philips.com
* Erik Moll - development of the Bluetooth SIG GHS and IEEE ACOM specifications - erik.moll@philips.com
* Martijn van Welie - lead author of the Blessed library - martijn.van.welie@philips.com

## License
This code is available under the [MIT license](LICENSE.md).
