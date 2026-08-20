ALTER TABLE recipe
  ADD COLUMN image_url VARCHAR(1024) NULL AFTER attribution_text,
  ADD COLUMN image_source_url VARCHAR(1024) NULL AFTER image_url,
  ADD COLUMN image_attribution VARCHAR(500) NULL AFTER image_source_url;

INSERT INTO recipe_source (id,name,source_type,license_code,attribution_text,allowed_use,source_version,enabled,created_at,updated_at)
SELECT UUID_TO_BIN(UUID()),'TheMealDB public API','PUBLIC_API','UPSTREAM_TERMS',
       'Recipe metadata and images: TheMealDB. Original recipe links are retained per recipe.',
       'Import with source attribution; upstream terms must be reviewed before redistribution.','api-v1',TRUE,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)
WHERE NOT EXISTS (SELECT 1 FROM recipe_source WHERE name='TheMealDB public API' AND source_version='api-v1');

SET @themealdb_source=(SELECT id FROM recipe_source WHERE name='TheMealDB public API' AND source_version='api-v1' LIMIT 1);
SET @m1=UUID_TO_BIN(UUID()); SET @m2=UUID_TO_BIN(UUID()); SET @m3=UUID_TO_BIN(UUID()); SET @m4=UUID_TO_BIN(UUID());
SET @m5=UUID_TO_BIN(UUID()); SET @m6=UUID_TO_BIN(UUID()); SET @m7=UUID_TO_BIN(UUID()); SET @m8=UUID_TO_BIN(UUID());

INSERT INTO recipe (id,source_id,source_recipe_id,source_version,origin,title,summary,cuisine,taste,goal,cook_minutes,servings,
                    calories_total,protein_total,fat_total,carbs_total,normalized_fingerprint,review_status,attribution_text,
                    image_url,image_source_url,image_attribution,created_at,updated_at) VALUES
(@m1,@themealdb_source,'53366','api-v1','IMPORTED','西兰花炒牛肉','嫩牛肉与爽脆西兰花的经典快炒。','中餐','咸鲜','高蛋白',25,4,NULL,NULL,NULL,NULL,SHA2('themealdb|53366',256),'APPROVED','Recipe metadata: TheMealDB; original recipe: Simply Recipes','https://www.themealdb.com/images/media/meals/m0p0j81765568742.jpg','https://www.simplyrecipes.com/recipes/broccoli_beef/','Image and recipe metadata: TheMealDB',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@m2,@themealdb_source,'52952','api-v1','IMPORTED','牛肉捞面','牛肉、面条和时蔬一锅快炒，适合工作日晚餐。','中餐','咸鲜','均衡',30,4,NULL,NULL,NULL,NULL,SHA2('themealdb|52952',256),'APPROVED','Recipe metadata: TheMealDB; original recipe: Sue and Gambo','https://www.themealdb.com/images/media/meals/1529444830.jpg','https://sueandgambo.com/pages/beef-lo-mein','Image and recipe metadata: TheMealDB',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@m3,@themealdb_source,'52956','api-v1','IMPORTED','鸡肉粥','米粥加入腌制鸡肉、姜和葱，温暖清淡。','中餐','清淡','均衡',45,4,NULL,NULL,NULL,NULL,SHA2('themealdb|52956',256),'APPROVED','Recipe metadata: TheMealDB; original recipe: Sue and Gambo','https://www.themealdb.com/images/media/meals/1529446352.jpg','https://sueandgambo.com/pages/chicken-congee','Image and recipe metadata: TheMealDB',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@m4,@themealdb_source,'53367','api-v1','IMPORTED','鸡肉炒饭','隔夜米饭搭配鸡肉、鸡蛋和蔬菜的家常炒饭。','中餐','咸鲜','均衡',35,6,NULL,NULL,NULL,NULL,SHA2('themealdb|53367',256),'APPROVED','Recipe metadata: TheMealDB; original recipe: Simply Recipes','https://www.themealdb.com/images/media/meals/wuyd2h1765655837.jpg','https://www.simplyrecipes.com/recipes/chicken_fried_rice/','Image and recipe metadata: TheMealDB',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@m5,@themealdb_source,'53365','api-v1','IMPORTED','香橙鸡','酥香鸡块裹上酸甜橙汁酱。','中餐','酸甜','均衡',40,4,NULL,NULL,NULL,NULL,SHA2('themealdb|53365',256),'APPROVED','Recipe metadata: TheMealDB; original recipe: Simply Recipes','https://www.themealdb.com/images/media/meals/s73ytv1765567838.jpg','https://www.simplyrecipes.com/recipes/chinese_orange_chicken/','Image and recipe metadata: TheMealDB',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@m6,@themealdb_source,'53372','api-v1','IMPORTED','番茄炒蛋','软嫩鸡蛋与多汁番茄的经典家常菜。','中餐','酸甜','均衡',15,4,NULL,NULL,NULL,NULL,SHA2('themealdb|53372',256),'APPROVED','Recipe metadata: TheMealDB; original recipe: Simply Recipes','https://www.themealdb.com/images/media/meals/rwvw8q1765660071.jpg','https://www.simplyrecipes.com/chinese-tomato-egg-recipe-7562056','Image and recipe metadata: TheMealDB',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@m7,@themealdb_source,'52955','api-v1','IMPORTED','蛋花汤','鸡汤中加入鸡蛋、蘑菇和青豆，十几分钟即可完成。','中餐','清淡','控制热量',15,4,NULL,NULL,NULL,NULL,SHA2('themealdb|52955',256),'APPROVED','Recipe metadata: TheMealDB; original recipe: Sue and Gambo','https://www.themealdb.com/images/media/meals/1529446137.jpg','https://sueandgambo.com/pages/egg-drop-soup','Image and recipe metadata: TheMealDB',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@m8,@themealdb_source,'52831','api-v1','IMPORTED','日式炸鸡','酱油与姜蒜腌制的日式酥炸鸡块。','日料','咸香','高蛋白',35,4,NULL,NULL,NULL,NULL,SHA2('themealdb|52831',256),'APPROVED','Recipe metadata and image: TheMealDB','https://www.themealdb.com/images/media/meals/tyywsw1505930373.jpg','https://www.themealdb.com/meal/52831','Image and recipe metadata: TheMealDB',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));

INSERT INTO recipe_component (id,recipe_id,name,role,quantity,unit,scaling_rule,minimum_quantity,maximum_quantity,sort_order) VALUES
(UUID_TO_BIN(UUID()),@m1,'牛里脊','PRIMARY',454,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@m1,'西兰花','SIDE',454,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@m1,'蚝油','SEASONING',30,'ml','BOUNDED',15,45,3),(UUID_TO_BIN(UUID()),@m1,'蒜','SEASONING',2,'piece','BOUNDED',1,4,4),
(UUID_TO_BIN(UUID()),@m2,'牛肉','PRIMARY',227,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@m2,'面条','PRIMARY',113,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@m2,'豆芽','SIDE',100,'g','LINEAR',NULL,NULL,3),(UUID_TO_BIN(UUID()),@m2,'蘑菇','SIDE',100,'g','LINEAR',NULL,NULL,4),
(UUID_TO_BIN(UUID()),@m3,'鸡肉','PRIMARY',227,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@m3,'大米','PRIMARY',100,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@m3,'姜','SEASONING',10,'g','BOUNDED',5,20,3),(UUID_TO_BIN(UUID()),@m3,'葱','SEASONING',15,'g','BOUNDED',5,30,4),
(UUID_TO_BIN(UUID()),@m4,'鸡腿肉','PRIMARY',454,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@m4,'米饭','PRIMARY',800,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@m4,'鸡蛋','PRIMARY',3,'piece','LINEAR',NULL,NULL,3),(UUID_TO_BIN(UUID()),@m4,'胡萝卜','SIDE',100,'g','LINEAR',NULL,NULL,4),
(UUID_TO_BIN(UUID()),@m5,'鸡腿肉','PRIMARY',650,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@m5,'橙子','SIDE',1,'piece','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@m5,'鸡蛋','PRIMARY',2,'piece','LINEAR',NULL,NULL,3),(UUID_TO_BIN(UUID()),@m5,'玉米淀粉','SIDE',100,'g','LINEAR',NULL,NULL,4),
(UUID_TO_BIN(UUID()),@m6,'番茄','PRIMARY',454,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@m6,'鸡蛋','PRIMARY',5,'piece','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@m6,'葱','SEASONING',1,'piece','BOUNDED',1,2,3),(UUID_TO_BIN(UUID()),@m6,'香油','SEASONING',5,'ml','BOUNDED',2,10,4),
(UUID_TO_BIN(UUID()),@m7,'鸡蛋','PRIMARY',1,'piece','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@m7,'鸡汤','PRIMARY',720,'ml','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@m7,'蘑菇','SIDE',50,'g','LINEAR',NULL,NULL,3),(UUID_TO_BIN(UUID()),@m7,'青豆','SIDE',50,'g','LINEAR',NULL,NULL,4),
(UUID_TO_BIN(UUID()),@m8,'鸡腿肉','PRIMARY',500,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@m8,'姜','SEASONING',15,'g','BOUNDED',8,25,2),(UUID_TO_BIN(UUID()),@m8,'蒜','SEASONING',3,'piece','BOUNDED',1,5,3),(UUID_TO_BIN(UUID()),@m8,'玉米淀粉','SIDE',80,'g','LINEAR',NULL,NULL,4);

INSERT INTO recipe_step (id,recipe_id,step_number,instruction_text) VALUES
(UUID_TO_BIN(UUID()),@m1,1,'牛肉切薄片，用酱油、料酒、淀粉和胡椒腌制 10 分钟。'),(UUID_TO_BIN(UUID()),@m1,2,'西兰花焯水约 2 分钟后沥干。'),(UUID_TO_BIN(UUID()),@m1,3,'大火炒熟牛肉和蒜，加入蚝油调成的酱汁与西兰花翻匀。'),
(UUID_TO_BIN(UUID()),@m2,1,'牛肉加盐、胡椒、香油和淀粉抓匀腌制。'),(UUID_TO_BIN(UUID()),@m2,2,'面条煮至刚熟，用冷水冲凉并沥干。'),(UUID_TO_BIN(UUID()),@m2,3,'依次炒牛肉和蔬菜，加入面条、蚝油和酱油大火翻匀。'),
(UUID_TO_BIN(UUID()),@m3,1,'鸡肉加盐、白胡椒和姜汁腌制。'),(UUID_TO_BIN(UUID()),@m3,2,'大米洗净，加水煮沸后转小火，间隔搅拌至米粒软烂。'),(UUID_TO_BIN(UUID()),@m3,3,'加入鸡肉再煮 10 分钟，以姜丝和葱花调味。'),
(UUID_TO_BIN(UUID()),@m4,1,'鸡肉切丁加盐腌制，鸡蛋炒散后盛出。'),(UUID_TO_BIN(UUID()),@m4,2,'鸡肉炒熟，再炒香洋葱、蒜、姜、胡萝卜和青豆。'),(UUID_TO_BIN(UUID()),@m4,3,'加入隔夜米饭、鸡肉和鸡蛋，以酱油、香油调味并炒匀。'),
(UUID_TO_BIN(UUID()),@m5,1,'橙汁、橙皮、酱油、米醋、淀粉和糖调成酱汁。'),(UUID_TO_BIN(UUID()),@m5,2,'鸡肉切块，依次裹蛋液和淀粉后炸至金黄熟透。'),(UUID_TO_BIN(UUID()),@m5,3,'炒香蒜和红葱头，倒入橙汁酱煮浓后裹匀鸡块。'),
(UUID_TO_BIN(UUID()),@m6,1,'番茄去蒂切块，鸡蛋打散。'),(UUID_TO_BIN(UUID()),@m6,2,'冷锅加油和蛋液，中火轻推至蛋液大致凝固后盛出。'),(UUID_TO_BIN(UUID()),@m6,3,'番茄炒软后调味，倒回鸡蛋翻炒并撒葱花。'),
(UUID_TO_BIN(UUID()),@m7,1,'鸡汤煮沸，加入盐、糖、白胡椒和香油。'),(UUID_TO_BIN(UUID()),@m7,2,'加入青豆与蘑菇，以水淀粉调至略浓。'),(UUID_TO_BIN(UUID()),@m7,3,'缓慢淋入打散的蛋液并轻推成蛋花，撒葱花。'),
(UUID_TO_BIN(UUID()),@m8,1,'鸡腿肉切块，用酱油、姜和蒜腌制至少 20 分钟。'),(UUID_TO_BIN(UUID()),@m8,2,'鸡肉沥去多余腌汁，均匀裹上玉米淀粉。'),(UUID_TO_BIN(UUID()),@m8,3,'分批炸至表面金黄、中心熟透，沥油后食用。');

INSERT INTO recipe_knowledge_chunk (id,recipe_id,chunk_type,content_text,source_version,attribution_text,index_status,created_at,updated_at)
SELECT UUID_TO_BIN(UUID()),r.id,'SUMMARY',CONCAT(r.title,' ',COALESCE(r.summary,''),' ',GROUP_CONCAT(c.name SEPARATOR ' ')),
       'api-v1',r.attribution_text,'MYSQL_INDEXED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)
FROM recipe r JOIN recipe_component c ON c.recipe_id=r.id
WHERE r.source_id=@themealdb_source AND r.source_recipe_id IN ('53366','52952','52956','53367','53365','53372','52955','52831')
GROUP BY r.id,r.title,r.summary,r.attribution_text;

INSERT INTO recipe_search_index_state (recipe_id,mysql_indexed_at,status,updated_at)
SELECT r.id,UTC_TIMESTAMP(3),'MYSQL_ONLY',UTC_TIMESTAMP(3)
FROM recipe r LEFT JOIN recipe_search_index_state s ON s.recipe_id=r.id
WHERE r.source_id=@themealdb_source AND s.recipe_id IS NULL;
