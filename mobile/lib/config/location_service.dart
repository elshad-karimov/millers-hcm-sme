import 'dart:math' as math;

import 'package:geolocator/geolocator.dart';

/// M496 — thin wrapper around `geolocator` for attendance clock-in.
///
/// Handles the permission request and a single position fix. The UI is
/// responsible for showing the consent notice (PRD S26 — the user must be told
/// their location is recorded) BEFORE calling [getPosition].
class LocationService {
  LocationService._();
  static final LocationService instance = LocationService._();

  /// Result of a position attempt: either a fix, or a reason it failed.
  Future<LocationResult> getPosition() async {
    try {
      final serviceOn = await Geolocator.isLocationServiceEnabled();
      if (!serviceOn) {
        return const LocationResult.denied(
            'Location services are turned off on this device.');
      }

      var perm = await Geolocator.checkPermission();
      if (perm == LocationPermission.denied) {
        perm = await Geolocator.requestPermission();
      }
      if (perm == LocationPermission.denied) {
        return const LocationResult.denied('Location permission was denied.');
      }
      if (perm == LocationPermission.deniedForever) {
        return const LocationResult.denied(
            'Location permission is permanently denied. Enable it in Settings.');
      }

      final pos = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: Duration(seconds: 15),
        ),
      );
      return LocationResult.ok(pos);
    } catch (e) {
      return LocationResult.denied('Could not get location: $e');
    }
  }

  /// Great-circle distance in metres between two lat/long points (haversine).
  static double distanceMeters(
      double lat1, double lon1, double lat2, double lon2) {
    const earthRadius = 6371000.0; // metres
    final dLat = _rad(lat2 - lat1);
    final dLon = _rad(lon2 - lon1);
    final a = math.sin(dLat / 2) * math.sin(dLat / 2) +
        math.cos(_rad(lat1)) *
            math.cos(_rad(lat2)) *
            math.sin(dLon / 2) *
            math.sin(dLon / 2);
    final c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a));
    return earthRadius * c;
  }

  static double _rad(double deg) => deg * math.pi / 180.0;
}

/// Outcome of a location attempt.
class LocationResult {
  final Position? position;
  final String? error;

  const LocationResult.ok(this.position) : error = null;
  const LocationResult.denied(this.error) : position = null;

  bool get isOk => position != null;
}
