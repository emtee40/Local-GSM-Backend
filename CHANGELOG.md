# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).


## [Unreleased]
### Added
- Not applicable

### Changed
- Not applicable

### Removed
- Not applicable

## [1.5.4] 2024-05-16
### Added
- Added Italian translation (thanks to Gabriele Monaco)
- Add a possibility to disable last known location
- Add a possibility to override accuracy from database

### Changed
- Mozilla location service will be shutdown. Use final export for now.

### Removed
- Removed apparently non-existent Vatican City MCC (thanks to Gabriele Monaco)

## [1.5.3] 2021-11-02
### Added
- Add ability to customize lacells URL (thanks to Micha LaQua)

### Changed
- Request legacy external storage as a workaround for setting optional database path

## [1.5.2] 2021-02-22
### Changed
- Updated Polish translations
- Some small fixes

## [1.5.1] 2021-01-21
### Changed
- Fixed downloading database from OpenCellId with new tokens

## [1.5.0] 2021-01-08
### Added
- Added a possibility to calculate area range based on signal strength. It can be enabled in options. Thanks to @ploink
- 5G cells support. Thanks to @ploink
- Remember last known location. Thanks to @ploink

### Changed
- Updated project to Android 10. Thanks to @ploink

## [1.4.23] 2020-12-18
### Changed
- Update url for mozilla database
- Add possibility to set custom url for mozilla database in settings
- Avoid using legacy function for LTE cell info, it fixes some dual sim configurations

## [1.4.22] 2018-06-17
### Changed
- Update Ukrainian Translation. Thanks to @burunduk
- Update build environment

## [1.4.21] 2018-01-26
### Changed
- Increase length of allowed external database path in advanced settings

## [1.4.20] 2018-01-20

### Changed
- Update German translation

## [1.4.19] 2018-01-08
### Changed
- Check if user interface fragment is attached before access.

## [1.4.18] 2017-09-24
### Added
- Issue 106: Show number of records in database on "Database" screen

### Changed
- Allow "summary" to be translated.
- Add Polish translation. Thanks to @verdulo

### Removed
- Not applicable

## [1.4.17] - 2017-07-28

### Changed
- [Crash report #101](https://github.com/n76/Local-GSM-Backend/issues/101)

## [1.4.16] - 2017-07-11

### Changed
- Fix bug #96: Allow newer style OpenCellId API keys

## [1.4.15] - 2017-07-03

### Changed
- Fix bug #100: Errors in XML on French translation

## [1.4.14] - 2017-06-08

### Changed
- Fix bug #96: Change URL for OpenCellId data download. Thanks to @IrrationalEscalation for pointing out the URL change. Automatic acquisition of OpenCellId API token/key still broken, but download with older key now works again.

## [1.4.13] - 2017-05-29

### Changed
- Fix bug #98: Crash on unexpected MMC/MNC values.

## [1.4.12] - 2017-05-29

### Changed
- Add French translation. Thanks to @Massedil

## [1.4.11] - 2017-02-13

### Changed
- Add Ukrainian translation. Thanks to @burunduk

## [1.4.10] - 2017-02-08

### Changed
- Update Serbian translation.
- Add Russian translation,
- Update build scripts.
- Thanks to Boris Kraut, Mladen Pejaković, @daktak and @Xottab-DUTY

## [1.4.9] - 2016-10-25

### Changed
- Make check for unexpectedly short file a bit more lenient.

## [1.4.8] - 2016-10-25

### Changed
- Kludge to work around incorrect length on OpenCellId ZIP files that cause failure at end of download.

## [1.4.7] - 2016-07-14

### Changed
- Brazilian Portugese translation thanks to @anonimou on XDA forums.

## [1.4.6] - 2016-07-14

### Changed
- Trigger scan for mobile/cell towers when location update requested by UnifiedNLP.

## [1.4.4] - 2016-05-23

### Changed
- Allow use of user defined database directory location. Fix negative progress indicator for large downloads.

## [1.4.3] - 2016-05-22

### Changed
- When asked for update, report current position (based on most recent changes reported by TelephonyManager) with current time.

## [1.4.2] - 2016-05-19

### Changed
- Only report new samples, not old ones with old times, to reduce debug logging in UnifiedNLP.

## [1.4.1] - 2016-05-06

### Changed
- Remove confusion about mobile country code 505 Australia and Norfolk Island in favor of Australia.

## [1.4.0] - 2016-05-04

### Added
- Support Marshmallow (Android 6.x) permissions.

## [1.3.7] - 2016-03-30

### Changed
- Revert changes from 1.3.4 to 1.3.5 as some users are reporting file permission issues.
- Fix crash introduced in v1.3.6.

## [1.3.6] - 2016-03-23

### Changed
- Support location aging in newest version of UnifiedNlp

## [1.3.5] - 2016-01-14

### Changed
- Remove all access and permissions required for access to old database storage location. If upgrading from a version prior to 1.3.3 you will need to re-generate your database and you should manually remove the old database from the /sdcard/.nogapps/ directory.

## [1.3.4] - 2016-01-02

### Changed
- Update Serbian translation

## [1.3.3] - 2015-12-29

### Changed
- Move database to reduce permissions needed.

## [1.3.2] - 2015-12-29

### Changed
- Improve download of lacells MCC extract files.

## [1.3.1] - 2015-12-29

### Changed
- Fix bug on downloading small files from lacells.

## [1.3.0] - 2015-12-28

### Added
- Thanks to @UnknownUntilNow Add import of cell tower data from per MCC extracts provided by @wvengen

## [1.2.1] - 2015-12-28

### Changed
- Correct report on processing time per record.

## [1.2.0] - 2015-12-27

### Added
- Thanks to @UnknownUntilNow Improve database download, new UI allows selection of countries by name for many countries.

## [1.1.0] - 2015-12-25

### Added
- Thanks to @UnknownUntilNow Improve database download, add German translation

## [1.0.7] - 2015-12-23

### Changed
- Revise target API to allow install on Gingerbread through Marshmallow

## [1.0.6] - 2015-12-21

### Changed
- Revise required API to allow install on Gingerbread

## [1.0.5] - 2015-12-20

### Changed
- Detect and better handle SQLite detected errors

## [1.0.4] - 2015-11-24

### Changed
- Thanks to @hogbush Support Marshmallow’s runtime permissions.

## [1.0.3] - 2015-11-22

### Changed
- Thanks to @pejakm Updated Serbian translation.

## [1.0.2] - 2015-11-22

### Changed
- Thanks to @hogbush Lots of code cleanup with better handling of UI elements.

## [1.0.1] - 2015-10-08

### Changed
- Restore compability with API 17

## [1.0.0] - 2015-08-14

### Changed
- Update Serbian translation.

## [0.9.4] - 2015-08-13

### Changed
- Slight revision to clean up logic, might help on https://github.com/n76/Local-GSM-Backend/issues/31 however the largest change is moving many text strings into string resources so that internationalization is better.

## [0.9.3] - 2015-08-03

### Changed
- Update Serbian translation.

## [0.9.2] - 2015-08-02

### Changed
- Fix bug where on download where towers were not being inserted into database.

## [0.9.1] - 2015-06-30

### Changed
- Improve acquisition of OpenCellID API key.

## [0.9.0] - 2015-06-29

### Added
- Add ability to acquire OpenCellID API key from within app (thanks to agilob)

## [0.8.0] - 2015-06-27

### Added
- Serbian translation (thanks to Mladen Pejaković)
- Conversion to use Android Studio and Gradle for building.
