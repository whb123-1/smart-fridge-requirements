ALTER TABLE assistant_action_proposal
  DROP CHECK chk_assistant_action_type,
  ADD CONSTRAINT chk_assistant_action_type CHECK (
    action_type IN (
      'CREATE_SHOPPING_CANDIDATE',
      'CREATE_RECIPE_CANDIDATES',
      'CREATE_MEAL_DRAFT',
      'CREATE_REMINDER_DRAFT',
      'NAVIGATE',
      'APP_ACTION'
    )
  );
