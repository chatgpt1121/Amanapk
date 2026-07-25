# Aman Remote — Powered by Aman

Bluetooth ya WiFi se do Android phones ko connect karke ek phone se doosre ko remote control karne wala app.

## Naya flow: Device List

App khulte hi ek **list dikhega** — jitne bhi phones tumne add/pair kiye hain, unke naam list me. **Kisi bhi phone pe tap karo aur uska pura remote control screen khul jayega** (Volume, Flashlight, Sleep, Wake, WiFi/Bluetooth panel — sab ek jagah).

### Home screen pe kya hai:
1. **"Make this phone controllable"** — is phone ko doosre se control hone ke liye taiyaar karta hai
   - **Bluetooth Listen** — Bluetooth se control hone ke liye
   - **WiFi Listen** — WiFi se control hone ke liye (IP address screen pe dikhega)
   - **Enable Sleep/Lock permission** — ek baar allow karo isi phone pe, tabhi doosra phone isko "Sleep" command bhej ke lock kar payega
2. **"Devices you can control"** — list of phones
   - Bluetooth se paired phones yaha automatically aa jaate hain
   - WiFi wale phones **"+ Add a WiFi device by IP"** se manually add karo (naam + IP dalke, ek baar save hoga, dobara nahi type karna padega)
3. Kisi bhi list item pe **tap karo** → us phone ka pura remote control screen khul jayega, auto-connect hoga, aur turant commands bhej sakte ho.

## Features (remote control screen pe)

| Feature | Status |
|---|---|
| Volume Up / Down | Fully automatic |
| Flashlight On / Off | Fully automatic |
| Screen Sleep (lock) | Automatic, lekin receiver phone pe "Enable Sleep/Lock permission" ek baar dabana hoga |
| Screen Wake | Fully automatic |
| WiFi On/Off | Settings panel khulta hai, receiver phone pe user ko tap karna padega |
| Bluetooth On/Off | Settings panel khulta hai, same reason |

**Note:** Android 10+ ne security ke liye apps ko WiFi/Bluetooth direct toggle karne se rok diya hai. Isliye ye feature "panel open + user tap" tarike se kaam karta hai.

Phone ko fully shutdown se remotely ON karna bhi possible nahi hai — jab phone off hota hai uska Bluetooth/WiFi radio bhi band ho jata hai.

## Setup steps (build karne ke liye)

1. Android Studio me is folder ko open karo (File > Open -> AmanRemote folder select karo).
2. Gradle sync hone do (pehli baar internet chahiye dependencies download karne ke liye).
3. Dono phones pe app install karo (USB debugging se ya APK banake).

### Bluetooth se control karna hai to:
1. Dono phones ko Bluetooth Settings me ek dusre se pehle pair kar lo (normal Android pairing, app ke bahar).
2. Receiver phone (jo control hoga) -> app kholo -> "Bluetooth Listen" dabao.
3. Controller phone (jo control karega) -> app kholo -> list me receiver ka naam automatically dikhega -> tap karo -> remote khul jayega.

### WiFi se control karna hai to (Bluetooth pairing dikkat de raha ho toh):
1. Dono phones same WiFi/hotspot pe hone chahiye.
2. Receiver phone -> app kholo -> "WiFi Listen" dabao -> screen pe uska IP dikhega (jaise 192.168.1.24).
3. Controller phone -> app kholo -> "+ Add a WiFi device by IP" -> naam aur wahi IP daalo -> Save.
4. List me wo naam dikhega -> tap karo -> remote khul jayega.

**Common WiFi issues:**
- Kuch office/college WiFi me "AP isolation" on hoti hai. Solution: ek phone ka mobile hotspot on karo, doosra phone usse connect karo, phir dono app pe WiFi mode try karo.
- Firewall/VPN on ho to bhi block ho sakta hai - VPN off karke try karo.

## Project Structure

```
AmanRemote/
├── app/
│   ├── src/main/java/com/aman/remote/
│   │   ├── MainActivity.kt              -> Home screen: device list + "make controllable" options
│   │   ├── RemoteControlActivity.kt     -> Ek device ka remote control screen (auto-connects + buttons)
│   │   ├── BluetoothConnectionService.kt-> Bluetooth connection handle karta hai (server + client)
│   │   ├── WifiConnectionService.kt     -> WiFi/local-network connection handle karta hai (server + client)
│   │   ├── WifiDeviceStore.kt           -> Saved WiFi devices (naam+IP) ki list store karta hai
│   │   ├── CommandExecutor.kt           -> received command ko actual action me convert karta hai
│   │   └── DeviceAdminReceiver.kt       -> sleep/lock feature ke liye zaroori
│   └── src/main/res/                    -> layouts, strings, colors
└── build.gradle, settings.gradle        -> project config
```

## Permissions ye app maangega

- Bluetooth (Connect/Scan)
- Location (Bluetooth discovery ke liye Android requirement)
- Camera (flashlight ke liye)
- Notifications (background service ke liye)
- Device Admin (sirf Sleep feature ke liye, optional prompt)
- Internet/WiFi state (WiFi mode ke liye)

---
**Powered by Aman**
