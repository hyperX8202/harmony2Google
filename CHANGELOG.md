# Changelog

## 1.01

- **Modified Aurora Store 4.8.4-hw9:** the installer is now chosen per app. Apps that EasyAbroad
  knows still go to EasyAbroad first. All other apps (K-9 Mail and many more) are installed through
  the session installer, which the container hands to 卓易通 (DroiTong). Before, every app that
  EasyAbroad does not know went through the forced microG installer, and some of them failed.
- The release build is now named `Gclick_1.01.apk`.

## 1.0

- First public release: setup wizard (permissions, gBART keyboard, EasyAbroad, Google apps, modified
  Aurora Store, Google sign-in, contacts, optional calendar) with the modified Aurora Store 4.8.4-hw7.
