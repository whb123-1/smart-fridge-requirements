-- Curated starter catalog. Each row is complete enough for matching, scaling and cooking.
SET @recipe_source = (SELECT id FROM recipe_source WHERE name='鲜知内置示例菜谱' AND source_version='1' LIMIT 1);

SET @r_chicken = UUID_TO_BIN(UUID());
SET @r_beef = UUID_TO_BIN(UUID());
SET @r_fish = UUID_TO_BIN(UUID());
SET @r_tofu = UUID_TO_BIN(UUID());
SET @r_shrimp = UUID_TO_BIN(UUID());
SET @r_pork = UUID_TO_BIN(UUID());
SET @r_salad = UUID_TO_BIN(UUID());
SET @r_oats = UUID_TO_BIN(UUID());
SET @r_noodle = UUID_TO_BIN(UUID());
SET @r_curry = UUID_TO_BIN(UUID());
SET @r_congee = UUID_TO_BIN(UUID());
SET @r_pumpkin = UUID_TO_BIN(UUID());

INSERT INTO recipe (id,source_id,source_recipe_id,source_version,origin,title,summary,cuisine,taste,goal,cook_minutes,servings,calories_total,protein_total,fat_total,carbs_total,normalized_fingerprint,review_status,attribution_text,created_at,updated_at)
VALUES
(@r_chicken,@recipe_source,'builtin-chicken-broccoli','1','CURATED','黑椒鸡胸肉西兰花','高蛋白快手餐，适合工作日晚餐。','轻食','咸鲜','增肌',20,2,620,52,18,32,SHA2('黑椒鸡胸肉西兰花|鸡胸肉|西兰花',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@r_beef,@recipe_source,'builtin-beef-onion','1','CURATED','洋葱牛肉片','牛肉搭配洋葱，鲜香下饭。','家常菜','咸鲜','均衡',18,2,680,46,38,28,SHA2('洋葱牛肉片|牛肉|洋葱',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@r_fish,@recipe_source,'builtin-steamed-fish','1','CURATED','清蒸鲈鱼','少油清蒸，保留鱼肉鲜味。','粤菜','清淡','控制热量',25,2,460,52,18,8,SHA2('清蒸鲈鱼|鲈鱼|姜|葱',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@r_tofu,@recipe_source,'builtin-mapotofu','1','CURATED','家常麻婆豆腐','豆腐与肉末的家常下饭菜。','川菜','微辣','均衡',25,2,560,30,32,30,SHA2('家常麻婆豆腐|豆腐|猪肉末',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@r_shrimp,@recipe_source,'builtin-shrimp-egg','1','CURATED','虾仁滑蛋','嫩滑高蛋白，十分钟即可完成。','家常菜','咸鲜','增肌',15,2,510,43,31,9,SHA2('虾仁滑蛋|虾仁|鸡蛋',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@r_pork,@recipe_source,'builtin-pork-pepper','1','CURATED','青椒肉丝','经典家常快炒，食材易准备。','家常菜','咸鲜','均衡',15,2,590,35,36,24,SHA2('青椒肉丝|猪里脊|青椒',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@r_salad,@recipe_source,'builtin-chicken-salad','1','CURATED','鸡胸肉蔬菜沙拉','无需复杂烹饪的清爽轻食。','轻食','清淡','减脂',12,1,330,34,12,22,SHA2('鸡胸肉蔬菜沙拉|鸡胸肉|生菜|玉米',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@r_oats,@recipe_source,'builtin-oats-yogurt','1','CURATED','酸奶水果燕麦杯','早餐或加餐，膳食纤维丰富。','轻食','清爽','控制热量',8,1,290,15,8,42,SHA2('酸奶水果燕麦杯|燕麦|酸奶|香蕉',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@r_noodle,@recipe_source,'builtin-tomato-noodle','1','CURATED','番茄鸡蛋面','一锅完成的暖胃主食。','家常菜','酸甜','均衡',18,2,610,25,20,82,SHA2('番茄鸡蛋面|番茄|鸡蛋|面条',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@r_curry,@recipe_source,'builtin-chicken-curry','1','CURATED','日式鸡肉咖喱','温和咖喱配鸡肉和土豆，适合全家。','日料','咸香','均衡',35,3,900,48,34,100,SHA2('日式鸡肉咖喱|鸡腿肉|土豆|胡萝卜',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@r_congee,@recipe_source,'builtin-fish-congee','1','CURATED','生滚鱼片粥','软糯易消化，适合早餐或恢复期。','粤菜','清淡','均衡',35,2,420,28,10,58,SHA2('生滚鱼片粥|大米|鱼片|姜',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@r_pumpkin,@recipe_source,'builtin-pumpkin-soup','1','CURATED','南瓜鸡肉浓汤','南瓜自然香甜，搭配鸡肉更有饱腹感。','轻食','清甜','减脂',30,3,510,31,14,58,SHA2('南瓜鸡肉浓汤|南瓜|鸡胸肉|牛奶',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));

INSERT INTO recipe_component (id,recipe_id,name,role,quantity,unit,scaling_rule,minimum_quantity,maximum_quantity,sort_order) VALUES
(UUID_TO_BIN(UUID()),@r_chicken,'鸡胸肉','PRIMARY',300,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@r_chicken,'西兰花','PRIMARY',250,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@r_chicken,'黑胡椒','SEASONING',2,'g','BOUNDED',1,4,3),(UUID_TO_BIN(UUID()),@r_chicken,'橄榄油','SEASONING',10,'ml','BOUNDED',5,18,4),
(UUID_TO_BIN(UUID()),@r_beef,'牛肉片','PRIMARY',280,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@r_beef,'洋葱','PRIMARY',180,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@r_beef,'生抽','SEASONING',15,'ml','BOUNDED',8,25,3),(UUID_TO_BIN(UUID()),@r_beef,'食用油','SEASONING',12,'ml','BOUNDED',6,20,4),
(UUID_TO_BIN(UUID()),@r_fish,'鲈鱼','PRIMARY',500,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@r_fish,'姜','SEASONING',15,'g','BOUNDED',8,25,2),(UUID_TO_BIN(UUID()),@r_fish,'葱','SEASONING',20,'g','BOUNDED',10,35,3),(UUID_TO_BIN(UUID()),@r_fish,'蒸鱼豉油','SEASONING',15,'ml','BOUNDED',8,22,4),
(UUID_TO_BIN(UUID()),@r_tofu,'北豆腐','PRIMARY',400,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@r_tofu,'猪肉末','PRIMARY',100,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@r_tofu,'郫县豆瓣酱','SEASONING',20,'g','BOUNDED',10,30,3),(UUID_TO_BIN(UUID()),@r_tofu,'花椒','SEASONING',2,'g','BOUNDED',1,4,4),
(UUID_TO_BIN(UUID()),@r_shrimp,'虾仁','PRIMARY',220,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@r_shrimp,'鸡蛋','PRIMARY',4,'piece','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@r_shrimp,'葱','SEASONING',15,'g','BOUNDED',8,25,3),(UUID_TO_BIN(UUID()),@r_shrimp,'食用油','SEASONING',10,'ml','BOUNDED',5,18,4),
(UUID_TO_BIN(UUID()),@r_pork,'猪里脊','PRIMARY',250,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@r_pork,'青椒','PRIMARY',180,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@r_pork,'生抽','SEASONING',12,'ml','BOUNDED',6,20,3),(UUID_TO_BIN(UUID()),@r_pork,'食用油','SEASONING',12,'ml','BOUNDED',6,20,4),
(UUID_TO_BIN(UUID()),@r_salad,'鸡胸肉','PRIMARY',150,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@r_salad,'生菜','PRIMARY',120,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@r_salad,'玉米','SIDE',80,'g','LINEAR',NULL,NULL,3),(UUID_TO_BIN(UUID()),@r_salad,'油醋汁','SEASONING',20,'ml','BOUNDED',10,30,4),
(UUID_TO_BIN(UUID()),@r_oats,'燕麦','PRIMARY',50,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@r_oats,'酸奶','PRIMARY',180,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@r_oats,'香蕉','SIDE',1,'piece','LINEAR',NULL,NULL,3),(UUID_TO_BIN(UUID()),@r_oats,'坚果','SEASONING',10,'g','BOUNDED',5,18,4),
(UUID_TO_BIN(UUID()),@r_noodle,'面条','PRIMARY',180,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@r_noodle,'番茄','PRIMARY',250,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@r_noodle,'鸡蛋','PRIMARY',2,'piece','LINEAR',NULL,NULL,3),(UUID_TO_BIN(UUID()),@r_noodle,'食用油','SEASONING',10,'ml','BOUNDED',5,18,4),
(UUID_TO_BIN(UUID()),@r_curry,'鸡腿肉','PRIMARY',350,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@r_curry,'土豆','PRIMARY',300,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@r_curry,'胡萝卜','SIDE',150,'g','LINEAR',NULL,NULL,3),(UUID_TO_BIN(UUID()),@r_curry,'咖喱块','SEASONING',60,'g','BOUNDED',30,90,4),
(UUID_TO_BIN(UUID()),@r_congee,'大米','PRIMARY',120,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@r_congee,'鱼片','PRIMARY',180,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@r_congee,'姜','SEASONING',10,'g','BOUNDED',5,18,3),(UUID_TO_BIN(UUID()),@r_congee,'葱','SEASONING',15,'g','BOUNDED',8,25,4),
(UUID_TO_BIN(UUID()),@r_pumpkin,'南瓜','PRIMARY',400,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@r_pumpkin,'鸡胸肉','PRIMARY',180,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@r_pumpkin,'牛奶','SIDE',250,'ml','LINEAR',NULL,NULL,3),(UUID_TO_BIN(UUID()),@r_pumpkin,'盐','SEASONING',2,'g','BOUNDED',1,4,4);

INSERT INTO recipe_step (id,recipe_id,step_number,instruction_text) VALUES
(UUID_TO_BIN(UUID()),@r_chicken,1,'鸡胸肉切片，用黑胡椒和少量盐腌制。'),(UUID_TO_BIN(UUID()),@r_chicken,2,'西兰花焯水，鸡胸肉煎至两面熟透。'),(UUID_TO_BIN(UUID()),@r_chicken,3,'加入西兰花翻拌，出锅前调味。'),
(UUID_TO_BIN(UUID()),@r_beef,1,'牛肉片擦干，洋葱切丝。'),(UUID_TO_BIN(UUID()),@r_beef,2,'热锅快炒牛肉至变色后盛出。'),(UUID_TO_BIN(UUID()),@r_beef,3,'炒香洋葱，倒回牛肉并用生抽调味。'),
(UUID_TO_BIN(UUID()),@r_fish,1,'鲈鱼洗净，在鱼身划刀并放姜片。'),(UUID_TO_BIN(UUID()),@r_fish,2,'水开后上锅蒸八至十分钟。'),(UUID_TO_BIN(UUID()),@r_fish,3,'倒掉蒸出的水，淋蒸鱼豉油并撒葱丝。'),
(UUID_TO_BIN(UUID()),@r_tofu,1,'豆腐切块，沥干水分。'),(UUID_TO_BIN(UUID()),@r_tofu,2,'炒香肉末和豆瓣酱，加水煮开。'),(UUID_TO_BIN(UUID()),@r_tofu,3,'放入豆腐小火煮八分钟，撒花椒出锅。'),
(UUID_TO_BIN(UUID()),@r_shrimp,1,'虾仁擦干，鸡蛋打散。'),(UUID_TO_BIN(UUID()),@r_shrimp,2,'虾仁炒至变色后盛出。'),(UUID_TO_BIN(UUID()),@r_shrimp,3,'蛋液入锅，半凝固时倒回虾仁并快速翻匀。'),
(UUID_TO_BIN(UUID()),@r_pork,1,'里脊切丝，青椒切丝。'),(UUID_TO_BIN(UUID()),@r_pork,2,'先将肉丝炒至变色。'),(UUID_TO_BIN(UUID()),@r_pork,3,'加入青椒和生抽，大火翻炒至断生。'),
(UUID_TO_BIN(UUID()),@r_salad,1,'鸡胸肉煮熟或煎熟后切片。'),(UUID_TO_BIN(UUID()),@r_salad,2,'生菜洗净，玉米焯熟。'),(UUID_TO_BIN(UUID()),@r_salad,3,'所有食材装盘，食用前淋油醋汁。'),
(UUID_TO_BIN(UUID()),@r_oats,1,'燕麦加入酸奶拌匀。'),(UUID_TO_BIN(UUID()),@r_oats,2,'冷藏至少一小时使其变软。'),(UUID_TO_BIN(UUID()),@r_oats,3,'食用前放上香蕉和坚果。'),
(UUID_TO_BIN(UUID()),@r_noodle,1,'番茄切块，鸡蛋打散。'),(UUID_TO_BIN(UUID()),@r_noodle,2,'炒蛋盛出，番茄炒出汁后加水。'),(UUID_TO_BIN(UUID()),@r_noodle,3,'放入面条煮熟，倒回鸡蛋调味。'),
(UUID_TO_BIN(UUID()),@r_curry,1,'鸡腿肉、土豆和胡萝卜切块。'),(UUID_TO_BIN(UUID()),@r_curry,2,'鸡肉煎香，加入蔬菜和水炖煮。'),(UUID_TO_BIN(UUID()),@r_curry,3,'关火放入咖喱块，融化后再煮五分钟。'),
(UUID_TO_BIN(UUID()),@r_congee,1,'大米淘洗后加水煮至开花。'),(UUID_TO_BIN(UUID()),@r_congee,2,'放入姜丝和鱼片，煮至鱼肉变白。'),(UUID_TO_BIN(UUID()),@r_congee,3,'加盐调味，撒葱花即可。'),
(UUID_TO_BIN(UUID()),@r_pumpkin,1,'南瓜去皮切块，鸡胸肉切丁。'),(UUID_TO_BIN(UUID()),@r_pumpkin,2,'南瓜加水煮软后打成泥。'),(UUID_TO_BIN(UUID()),@r_pumpkin,3,'加入鸡肉和牛奶煮熟，最后加盐调味。');

INSERT INTO recipe_knowledge_chunk (id,recipe_id,chunk_type,content_text,source_version,attribution_text,index_status,created_at,updated_at)
SELECT UUID_TO_BIN(UUID()),id,'SUMMARY',CONCAT(title,' ',COALESCE(summary,''),' ',title),'1','鲜知内置营养与做法数据','MYSQL_INDEXED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)
FROM recipe WHERE source_id=@recipe_source AND source_recipe_id LIKE 'builtin-%';
INSERT INTO recipe_search_index_state (recipe_id,mysql_indexed_at,status,updated_at)
SELECT id,UTC_TIMESTAMP(3),'MYSQL_ONLY',UTC_TIMESTAMP(3) FROM recipe WHERE source_id=@recipe_source AND source_recipe_id LIKE 'builtin-%';
