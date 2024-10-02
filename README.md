# <img src="https://github.com/Radiokot/osmand-display-app/raw/main/app/src/main/res/mipmap-hdpi/ic_launcher.png" alt="Icon" style="vertical-align: bottom; height: 1.2em;"/> Bike navigation display app
A companion Android application for the [wireless bike navigation display](https://github.com/Radiokot/osmand-display).

The app does:
- Compose map frames and send them to the display
- Import and store GPX and GeoJSON tracks, BRouter routes
- Download map areas for imported tracks to work offline
- Broadcast step-by-step navigation directions from OsmAnd, which was the original purpose of the display, but turned out to be useless

<p float="left">
  <img src="https://user-images.githubusercontent.com/5675681/210539138-c739653d-2bbe-474f-b061-48d86704e82c.png" width="400" alt="Prototyping"/>
  <img src="https://user-images.githubusercontent.com/5675681/212536029-b758ba95-dd6e-4a5c-be77-41d6c6c35408.png" width="225" alt="On a bike"/>
  <img src="https://user-images.githubusercontent.com/5675681/212535953-71a9e10d-2c91-4f38-80ef-dce9b940366c.png" width="135" alt="The app"/>
</p>

## Tech stack
- Kotlin
- RxJava
- Koin dependency injection
- OsmAnd AIDL interface
- Mapbox map SDK
- BLESSED BLE library
- kotlin-logging with slf4j-handroid
