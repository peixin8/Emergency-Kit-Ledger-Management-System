ALTER TABLE emergency_device_ledger ADD COLUMN latitude DECIMAL(10,7);
ALTER TABLE emergency_device_ledger ADD COLUMN longitude DECIMAL(10,7);
ALTER TABLE emergency_device_ledger ADD CONSTRAINT chk_ledger_coordinates_pair
  CHECK ((latitude IS NULL AND longitude IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL));
ALTER TABLE emergency_device_ledger ADD CONSTRAINT chk_ledger_latitude
  CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90);
ALTER TABLE emergency_device_ledger ADD CONSTRAINT chk_ledger_longitude
  CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180);
