CREATE TABLE planned_recipe (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  fridge_id BINARY(16) NOT NULL,
  recipe_id BINARY(16) NOT NULL,
  servings DECIMAL(10,3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_planned_recipe_user_fridge_recipe (user_id, fridge_id, recipe_id),
  KEY ix_planned_recipe_fridge_created (fridge_id, created_at),
  CONSTRAINT fk_planned_recipe_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_planned_recipe_fridge FOREIGN KEY (fridge_id) REFERENCES fridge(id),
  CONSTRAINT fk_planned_recipe_recipe FOREIGN KEY (recipe_id) REFERENCES recipe(id),
  CONSTRAINT ck_planned_recipe_servings CHECK (servings > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
