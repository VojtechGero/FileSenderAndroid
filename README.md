# FileSender Android

Android aplikace pro posílání fotek a dalších souborů do počítače přes místní síť. Vybereš příjemce, přidáš přílohy a odešleš je. Počítač soubory přijme do paměti a nabídne jejich uložení na disk.

Pro příjem na Windows použij [FileSender Desktop](https://github.com/VojtechGero/FileSenderDesktop).

## Co aplikace umí

- Najít počítače s FileSenderem automaticky v místní síti.
- Připojit se přes QR kód z desktopové aplikace nebo zadat adresu ručně.
- Vybrat více fotek přes samostatné tlačítko **Vybrat fotky**.
- Vybrat dokumenty, archivy a další typy přes **Vybrat soubory**.
- Kombinovat výběry, odebírat jednotlivé přílohy nebo vymazat celý seznam.
- Odeslat více souborů postupně a zobrazit průběh dávky i případné chyby.
- Používat světlý nebo tmavý vzhled podle nastavení systému.

Rozhraní je v češtině a má dvě záložky. **Odeslat** obsahuje příjemce a přílohy, **Zařízení** slouží k hledání a připojení počítače.

## Rychlý start

1. Připoj telefon a počítač ke stejné místní síti.
2. Na počítači spusť FileSender Desktop a zapni **Spustit příjem**.
3. Na telefonu v záložce **Zařízení** vyber nalezený počítač.
4. Pokud ho nevidíš, zvol **Skenovat QR** a načti kód z desktopové obrazovky **Připojení**. Můžeš také ručně zadat adresu jako `http://192.168.1.10:5000`.
5. Vyber fotky či soubory a klepni na **Odeslat**.
6. Na počítači ulož přijaté soubory přes **Uložit jako…**.

Přílohy můžeš připravit i před výběrem příjemce. Přepínání mezi záložkami je zachová.

## Hledání a obnovení zařízení

Telefon hledá počítače pomocí Android Network Service Discovery a služby `_filesender._tcp`. Při změnách v síti aktualizuje nalezená zařízení podle oznámení mDNS.

Tlačítko **Obnovit** znovu spustí hledání a jednorázově ověří dostupnost známých počítačů i vybraného příjemce přes `/ping`. Nedostupný počítač odebere ze zobrazeného seznamu a případně zruší jeho výběr. Pravidelný časovač pro HTTP kontroly aplikace nepoužívá.

Samostatné **Otestovat spojení** ověří aktuální adresu na vyžádání.

## Požadavky

| Oblast | Požadavek |
| --- | --- |
| Telefon | Android 8.0 nebo novější, API 26+ |
| Příjemce | FileSender Desktop na Windows |
| Síť | Přímá komunikace telefonu s počítačem, TCP 5000 |
| Automatické hledání | Průchozí multicast mDNS, UDP 5353 |
| Sestavení | Android SDK Platform 37 a nástroje kompatibilní s projektem |

Pro běžný přenos lokálních souborů a QR párování stačí místní síť. Výběr souboru uloženého u cloudového poskytovatele může vyžadovat jeho stažení.

Kameru aplikace potřebuje při skenování QR. Na systémech vyžadujících oprávnění pro místní síť si o něj řekne při připojení či hledání. Soubory vybírá přes systémové dialogy Androidu.

## Sestavení

Otevři kořen repozitáře v Android Studiu s podporou Android Gradle Plugin 9.4.1 a SDK 37. Nech proběhnout Gradle Sync a připoj telefon nebo spusť emulátor.

Projekt obsahuje Gradle Wrapper 9.6.0 a konfiguraci JVM toolchainu 25 v `gradle/gradle-daemon-jvm.properties`. První synchronizace může stáhnout potřebnou JVM a závislosti. Cestu k Android SDK nastav ve svém prostředí nebo v lokálním souboru `local.properties`.

Na Windows spusť v PowerShellu

```powershell
.\gradlew.bat assembleDebug
```

Na Linuxu nebo macOS

```sh
sh ./gradlew assembleDebug
```

Výsledné APK najdeš v `app/build/outputs/apk/debug/app-debug.apk`. Pro instalaci na připojené zařízení můžeš použít `installDebug` místo `assembleDebug`.

V Android emulátoru odpovídá adresa `http://10.0.2.2:5000` hostitelskému počítači. Na fyzickém telefonu použij IPv4 adresu počítače v místní síti.

## Když spojení nefunguje

- Ověř, že na desktopu běží příjem a telefon je ve stejné místní síti.
- V záložce **Zařízení** klepni na **Obnovit** a případně vyzkoušej QR či ruční adresu.
- Zkontroluj firewall Windows a správnou adresu při více síťových adaptérech nebo VPN.
- QR obejde potíže s automatickým nalezením, přenos však stále potřebuje přímé HTTP spojení. Školní a hostovské sítě mohou zařízení vzájemně izolovat.
- Po chybě dávky zkontroluj seznam neodeslaných souborů. Při opakovaném odeslání celé dávky dostane desktop znovu i dříve úspěšné soubory.

## Současná omezení

Aplikace je určená pro důvěryhodnou domácí síť. Přenos používá HTTP bez šifrování, přihlášení a ověření identity příjemce. Desktopový port 5000 nevystavuj do internetu.

Soubory se posílají po jednom jako binární stream v původním formátu. Na desktopu zůstávají v paměti do ručního uložení, při ukončení desktopu se neuložený obsah ztratí. Přenos na pozadí, obnovení přerušeného souboru a trvalá historie zatím nejsou implementované.

## Technologie a zdrojový kód

Kotlin, Jetpack Compose, Material 3, Android NSD, `HttpURLConnection` a ZXing Android Embedded pro QR skener.

| Soubor | Úloha |
| --- | --- |
| `MainActivity.kt` | Obrazovky, oprávnění a řízení odesílání |
| `TransferUi.kt` | Sdílené prvky rozhraní |
| `DeviceDiscovery.kt` | Vyhledávání desktopů přes NSD |
| `FileSelector.kt` | Systémové výběry a seznam příloh |
| `ServerClient.kt` | HTTP přenos a kontrola dostupnosti |
| `SelectedFile.kt`, `SelectedImage.kt` | Přístup k souborům a náhled fotky |

Zdrojové soubory jsou v `app/src/main/java/com/example/filesender/`. Verze závislostí jsou v `gradle/libs.versions.toml`.
