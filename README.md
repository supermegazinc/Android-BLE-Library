# Supermegazinc's BLE

This library is intended to extend the power of the Android Bluetooth module by taking advantage of the coroutine API, Flow, and usage changes that will make the experience of implementing BLE in your application much, MUCH more enjoyable.

This library is involved in most BLE use cases, from requesting adapter power-on to receiving messages. In other words, this library can replace the Android Bluetooth module in most use cases.

Another feature worth highlighting is that the library is prepared to create test implementations, which would be useful for use cases where you want to test a Bluetooth connection under specific parameters without having a Bluetooth device.

# Features

## Adapter
* Automatic status detection: You can know the status of the Bluetooth adapter in real-time (on, off, not available).
* Request Adapter Power On: Provides utilities to facilitate adapter power-on in Jetpack Compose

## Scanner
* Filter by Service UUID
* Scanned devices are updated in real-time

## Device
The BLEDevice class allows you to manage the connection of an specific device, such as:
* Connecting
* Services and Characteristics

## Services and Characteristics
* Services and characteristics are updated in real time
* Characteristics are automatically discovered and subscribed to real time changes

## Controller
BLEController is the class that centralize all the features above, so its the only instance you will create.

# Installation

## Github package (easy way)

  1. Go to https://github.com/settings/tokens and create a new token with `read:packages`

  2. In your project root folder, create the file `github.properties` and add the following:

      ```Gradle
      gpr.usr=YOUR_USER
      gpr.key=YOUR_TOKEN  
      ```

  3. Open your `settings.gradle.kts` file and add the dependencies. Should look like:

      ```Gradle
      dependencyResolutionManagement {
          repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
          repositories {
              google()
              mavenCentral()

              val githubProperties = Properties()
              githubProperties.load(FileInputStream("./github.properties"))

              githubPackages.forEach { packageUrl ->
                  maven {
                      name = "GitHubPackages"
                      url = uri("https://maven.pkg.github.com/supermegazinc/Android-Libraries")
                      credentials {
                          username = githubProperties["gpr.usr"] as String?
                          password = githubProperties["gpr.key"] as String?
                      }
                  }
              }
          }
      }
      ```

  4. Open your `build.gradle.kts` (app) and add the dependency:

      ```Gradle
      implementation("com.supermegazinc.libraries:ble:VERSION")
      ```

      Change the version to the latest
  
## Manually (for coding and debugging)

  1. Download the source code
  2. Open your project root folder and create a new folder with the desired name for this library (eg. "ble_library")
  3. Extract the code in the folder
  4. Inside ble_library folder, create `github.properties` and fill it as above
  5. In your "settings.gradle.kts" add the following lines in the end:
      ```Gradle
      include(":ble_library")
      ```
  6. In your build.gradle.kts (app) add the following dependencies: 
      ```Gradle
      implementation(project(":ble_library"))
      ```
  7. Funny huh?

## Set-Up the permissions
Finally something that isn't my fault, let's open the manifest.xml and edit it like this:
```XML
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.BLUETOOTH"/>
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation"
        tools:targetApi="s" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-feature android:name="android.hardware.bluetooth" />
    <uses-feature android:name="android.hardware.bluetooth_le" />

<application
```

Now, at runtime, you have to request the required permissions based on the Android version, which by the way is another task that suck and I'll be uploading a library to fix that soon.
For testing purposes, you can manually grant all the permissions in the app info.

# Usage

1. Create the controller
   
   In most of the cases you will want to create a global singleton instance for dependency injection (eg. using Hilt)
   ```Kotlin
    bleController = BLEControllerImpl(
      context = applicationContext,
      logger = logger,
      coroutineScope = CoroutineScope(Dispatchers.IO)
    )
   ```
   
2. Scan devices
   
   ```Kotlin
   //In case you want to filter by Service UUID (can be null)
   val servicesUUID = listOf(UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914c"))
   bleController.scanner.start(servicesUUID)
   ```
   
   Now you can access the scanned devices in real time using ```bleController.scanner.scannedDevices```
   
   Now, for example, you can find a specific device for a determined period of time:

   ```Kotlin
    val scannedDevice = try {
      bleController.scanner.scannedDevices
      .map { scannedDevices->
        scannedDevices.firstOrNull { scannedDevice ->
        name == scannedDevice.name
        }
      }
      .filterNotNull()
      .waitForNextWithTimeout(10000)
   } catch (_: TimeoutCancellationException) {
     //not found
     null
   }
   ```

   After scanning don't forget to do:
   ```Kotlin
   bleController.scanner.stop()   //To stop scanning
   bleController.scanner.clear()  //To clear the scanned devices list
   ```

3. Set the device
   
   Setting the device will create a new BLEDevice that contains everything necessary to perform a connection.

    ```Kotlin
    val bleDevice = bleController.setDevice(scannedDevice.mac, mtu) //Where MTU is the maximum amount of bytes to be transferred by packet (from 23 to 517)
    if(bleDevice==null) {
      //error
    }
    ```

    You can also keep track of the device in real time by using ```bleController.device //StateFlow<BLEDevice?>```

4. Connect to the device
   
   All your effort is compensated by simply doing:
   
    ```Kotlin
    val connectionResult = bleDevice.connect()
    when(connectionResult) {
      is Result.Fail -> {
          when(result.error) {
              BLEGattConnectError.CANT_CONNECT -> TODO()
              BLEGattConnectError.TIMEOUT -> TODO()
              BLEGattConnectError.CANCELED -> TODO()
          }
      }
      is Result.Success -> {
          //You did it
      }
    }
    ```
     
    About the failures:
    * CANT_CONNECT: The device has terminated the connection
    * TIMEOUT: The device did not respond for at least 10 seconds
    * CANCELED: bleDevice.connect() was triggered again during this attempt

    You can also keep track of the connection in real time using ```bleDevice.status //StateFlow<BLEDeviceStatus>```
    
    Where BLEDeviceStatus can be:
    * Connected
    * Disconnected(val reason: BLEDisconnectionReason)
    * Connecting

5. Using services and characteristics
   
   Services and characteristics are automatically discovered and you can keep track of them in real time using ```bleDevice.services //StateFlow<List<BLEDeviceService>>``` and ```bleDevice.characteristics //StateFlow<List<BLEDeviceCharacteristic>>```

   Now, for example, you can find a specific characteristic by its UUID:

     ```Kotlin
      foundCharacteristic = bleDevice
        .characteristics
        .map { it.firstOrNull {characteristic -> customUUID == characteristic.uuid} }
        .filterNotNull()
        .first()
      ```

   Once you select a characteristic, you can:
   * Set real-time notifications

     ```Kotlin
       foundCharacteristic.setNotification(true)
       foundCharacteristic.message.collect {incomingMessage-> }
      ```
     
     Turning on real-time notification will automatically receive the messages and place them in ```BLEDeviceCharacteristic::message //StateFlow<ByteArray?>```

   * Read the characteristic
     
     ```Kotlin
       foundCharacteristic.forceRead()
       val msg = foundCharacteristic.message.waitForNext()
      ```
     
      Useful when the device does not support notification.

   * Send a message
     
     ```Kotlin
       foundCharacteristic.send(message: ByteArray)
      ```

# Final Notes

This is one of my personal tools that I frequently use in my projects and I'm not planning to upload it to Maven Central nor any other repository, but feel free to use it and modify it in any way.
