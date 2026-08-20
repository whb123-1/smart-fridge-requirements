-- Imported recipes from TheMealDB did not carry nutrition fields. These values
-- are the reviewed AI catalog-search estimates used by the recipe UI until a
-- source revision supplies more precise measurements.
UPDATE recipe
   SET calories_total = CASE source_recipe_id
         WHEN '53366' THEN 950
         WHEN '52952' THEN 900
         WHEN '52956' THEN 650
         WHEN '53367' THEN 1700
         WHEN '53365' THEN 1900
         WHEN '53372' THEN 650
         WHEN '52955' THEN 300
         WHEN '52831' THEN 1700
       END,
       protein_total = CASE source_recipe_id
         WHEN '53366' THEN 100
         WHEN '52952' THEN 70
         WHEN '52956' THEN 55
         WHEN '53367' THEN 115
         WHEN '53365' THEN 125
         WHEN '53372' THEN 35
         WHEN '52955' THEN 25
         WHEN '52831' THEN 120
       END,
       fat_total = CASE source_recipe_id
         WHEN '53366' THEN 35
         WHEN '52952' THEN 25
         WHEN '52956' THEN 12
         WHEN '53367' THEN 45
         WHEN '53365' THEN 60
         WHEN '53372' THEN 40
         WHEN '52955' THEN 12
         WHEN '52831' THEN 80
       END,
       carbs_total = CASE source_recipe_id
         WHEN '53366' THEN 40
         WHEN '52952' THEN 120
         WHEN '52956' THEN 85
         WHEN '53367' THEN 205
         WHEN '53365' THEN 220
         WHEN '53372' THEN 35
         WHEN '52955' THEN 25
         WHEN '52831' THEN 110
       END,
       nutrition_source = 'AI_CATALOG_SEARCH'
 WHERE source_recipe_id IN ('53366','52952','52956','53367','53365','53372','52955','52831')
   AND (calories_total IS NULL OR protein_total IS NULL OR fat_total IS NULL OR carbs_total IS NULL);

-- Keep future imported records from silently appearing without a nutrition
-- source label; import-time AI enrichment can replace these values later.
UPDATE recipe
   SET nutrition_source = COALESCE(NULLIF(nutrition_source, ''), 'AI_CATALOG_SEARCH')
 WHERE nutrition_source IS NULL OR nutrition_source = '';
