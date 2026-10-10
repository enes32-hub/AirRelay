# Physical test devices

On 31 August 2026 the user specified the first test pair as an **iPhone 14 Pro** and a **Xiaomi TV Box 4K (described as the latest generation)**.

The iOS version/build, Xiaomi's exact model number, the Android version/build and physical ADB access have not been verified yet. The [machine-readable record](DEVICE_COMPATIBILITY.json) keeps these fields as unknown. No support approval is given here.

The exact model number on the TV box's product label and the model and Android version in Settings → Device Preferences/System → About will be used; menu names may vary with the device version. The iPhone version can be read from the Settings → General → About screen. Device serial number, account information, MAC address or PIN do not need to be shared.

The first physical attempt will use the **Control Center → Screen Mirroring** flow on the iPhone. No helper application will be installed on the iPhone. The interface brand of the Xiaomi box on top of Android TV does not mean that we will also develop an application for another platform.

One device pair passing only proves that model/OS/network combination. The wider iPhone version and Android TV hardware matrix, the 2/8 hour endurance and the other acceptance thresholds in the specification must also be satisfied separately.

## Test order

In line with the user's instruction of 31 August 2026, daily development and intermediate verification will be done on Android TV emulators. The real Xiaomi TV will be used only in the final physical acceptance stage; before that, no continuous installation or trial on the TV will be requested. Emulator results are evidence for the D-pad, the interface, the service life cycle and basic package installation; they do not replace acceptance of iPhone discovery, H.264 hardware decoding, audio/video quality or real network behaviour.

## Test connection to the computer

The exact model and Android version must be verified first. The [official ADB documentation](https://developer.android.com/tools/adb) states the Android 13+ requirement on the TV for wireless debugging with a pairing code; the Android 11 limit on phones should not be applied to the TV. If the manufacturer's menu does not offer this feature, the connection path supported by the model must be determined separately. No random IP scanning, port forwarding or authorisation bypass will be done.

On a trusted local network the device owner enables debugging from the TV's developer options and authorises only their own computer. For a connection that requires a code, `adb pair IP:PAIRING_PORT` is used; the code is entered at the prompt in the local terminal, it is not written into the chat or into a file. On the USB path the RSA confirmation on the TV is given by the device owner. This preparation does not modify the TV or install an application; debugging must be turned off and the computer's authorisation removed once the test is over.

After the connection is established, to read only the selected TV's model, OS, ABI and TV features:

```powershell
python scripts/collect_tv_environment.py --serial 'TV_IN_ADB_LIST'
```

The tool does not do automatic pairing/connection, does not install an application, and does not collect all system properties or logs. The ADB address/serial number is not written into the report. The output is in the file `artifacts/local/tv-environment.json`, which is kept outside Git. Emulators are rejected; a zero exit code only indicates that metadata was read, it does not prove physical device identity or mirroring success. At present, physical Xiaomi access has not been verified.
