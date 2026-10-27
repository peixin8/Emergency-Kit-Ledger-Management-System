CREATE TABLE organization (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_organization_name UNIQUE (name)
);

CREATE TABLE app_user (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  username VARCHAR(80) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(16) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_app_user_username UNIQUE (username),
  CONSTRAINT fk_user_organization FOREIGN KEY (organization_id) REFERENCES organization(id)
);

CREATE TABLE emergency_device_ledger (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  ledger_code VARCHAR(64) NOT NULL,
  original_address VARCHAR(255) NOT NULL,
  destination_address VARCHAR(255) NOT NULL,
  application_method VARCHAR(20) NOT NULL,
  applicant_contact VARCHAR(100) NOT NULL,
  applicant_phone VARCHAR(40),
  device_condition VARCHAR(20) NOT NULL,
  direction VARCHAR(20) NOT NULL,
  application_date DATE NOT NULL,
  access_room VARCHAR(120),
  fiber_core VARCHAR(100),
  network_standard VARCHAR(100),
  bbu_model VARCHAR(120),
  rru_model VARCHAR(120),
  bbu_rru_power_watts DECIMAL(12,2),
  management_ip VARCHAR(45),
  management_vlan INT,
  business_ip VARCHAR(45),
  business_vlan INT,
  device_serial_no VARCHAR(120),
  asset_owner VARCHAR(120),
  remarks VARCHAR(2000),
  version BIGINT NOT NULL DEFAULT 0,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_ledger_org_code UNIQUE (organization_id, ledger_code),
  CONSTRAINT fk_ledger_org FOREIGN KEY (organization_id) REFERENCES organization(id)
);
CREATE INDEX idx_ledger_org_deleted_date ON emergency_device_ledger(organization_id, deleted, application_date);

CREATE TABLE ledger_audit (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  ledger_id BIGINT NOT NULL,
  actor_user_id BIGINT NOT NULL,
  action VARCHAR(16) NOT NULL,
  old_value TEXT,
  new_value TEXT,
  trace_id VARCHAR(40) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_audit_ledger FOREIGN KEY (ledger_id) REFERENCES emergency_device_ledger(id),
  CONSTRAINT fk_audit_user FOREIGN KEY (actor_user_id) REFERENCES app_user(id)
);
CREATE INDEX idx_audit_ledger ON ledger_audit(organization_id, ledger_id, created_at);
