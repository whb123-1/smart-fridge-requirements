ALTER TABLE recipe
  ADD COLUMN created_by BINARY(16) NULL AFTER origin,
  ADD KEY ix_recipe_draft_owner (created_by, review_status, created_at),
  ADD CONSTRAINT fk_recipe_created_by FOREIGN KEY (created_by) REFERENCES app_user(id);
