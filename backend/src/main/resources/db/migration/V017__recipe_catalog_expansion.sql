-- Additional everyday recipes keep the catalog useful before an external source is imported.
SET @recipe_source = (SELECT id FROM recipe_source WHERE name='鲜知内置示例菜谱' AND source_version='1' LIMIT 1);
SET @x1=UUID_TO_BIN(UUID()); SET @x2=UUID_TO_BIN(UUID()); SET @x3=UUID_TO_BIN(UUID()); SET @x4=UUID_TO_BIN(UUID());
SET @x5=UUID_TO_BIN(UUID()); SET @x6=UUID_TO_BIN(UUID()); SET @x7=UUID_TO_BIN(UUID()); SET @x8=UUID_TO_BIN(UUID());
SET @x9=UUID_TO_BIN(UUID()); SET @x10=UUID_TO_BIN(UUID()); SET @x11=UUID_TO_BIN(UUID()); SET @x12=UUID_TO_BIN(UUID());
INSERT INTO recipe (id,source_id,source_recipe_id,source_version,origin,title,summary,cuisine,taste,goal,cook_minutes,servings,calories_total,protein_total,fat_total,carbs_total,normalized_fingerprint,review_status,attribution_text,created_at,updated_at) VALUES
(@x1,@recipe_source,'builtin-garlic-broccoli','1','CURATED','蒜蓉西兰花','清爽快手蔬菜，适合作为配菜。','家常菜','清淡','控制热量',10,2,220,10,12,18,SHA2('蒜蓉西兰花|西兰花',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@x2,@recipe_source,'builtin-mushroom-chicken','1','CURATED','香菇鸡丁','鸡肉和香菇鲜味浓郁，少油也好吃。','家常菜','咸鲜','增肌',22,2,570,48,28,22,SHA2('香菇鸡丁|鸡胸肉|香菇',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@x3,@recipe_source,'builtin-salmon-avocado','1','CURATED','三文鱼牛油果碗','优质脂肪和蛋白质组合，免开火轻食。','轻食','清爽','均衡',12,1,520,30,28,34,SHA2('三文鱼牛油果碗|三文鱼|牛油果',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@x4,@recipe_source,'builtin-tuna-corn-rice','1','CURATED','金枪鱼玉米饭团','罐头食材也能快速做出便携主食。','日料','咸鲜','均衡',15,2,640,32,16,88,SHA2('金枪鱼玉米饭团|金枪鱼|玉米|米饭',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@x5,@recipe_source,'builtin-seaweed-soup','1','CURATED','紫菜蛋花汤','五分钟完成的家常汤品。','家常菜','清淡','控制热量',8,2,180,14,9,8,SHA2('紫菜蛋花汤|紫菜|鸡蛋',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@x6,@recipe_source,'builtin-wintermelon-shrimp','1','CURATED','冬瓜虾皮汤','清淡低热量，适合炎热天气。','家常菜','清淡','减脂',20,2,160,16,5,15,SHA2('冬瓜虾皮汤|冬瓜|虾皮',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@x7,@recipe_source,'builtin-eggplant-beans','1','CURATED','茄子豆角','家常下饭素菜，口感软嫩。','家常菜','咸鲜','均衡',20,2,420,12,26,38,SHA2('茄子豆角|茄子|豆角',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@x8,@recipe_source,'builtin-hot-sour-potato','1','CURATED','酸辣土豆丝','爽脆开胃的快炒菜。','川菜','微辣','均衡',12,2,380,8,18,48,SHA2('酸辣土豆丝|土豆|醋|辣椒',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@x9,@recipe_source,'builtin-korean-bibimbap','1','CURATED','牛肉蔬菜拌饭','一碗包含主食、蛋白质和多种蔬菜。','日料','微辣','均衡',25,2,760,40,25,94,SHA2('牛肉蔬菜拌饭|牛肉|菠菜|胡萝卜|米饭',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@x10,@recipe_source,'builtin-beef-wrap','1','CURATED','牛肉蔬菜卷','薄饼包裹牛肉和蔬菜，方便分享。','轻食','咸鲜','均衡',20,2,680,42,24,64,SHA2('牛肉蔬菜卷|牛肉|生菜|薄饼',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@x11,@recipe_source,'builtin-banana-pancake','1','CURATED','香蕉鸡蛋饼','不加糖的简单早餐，孩子也喜欢。','家常菜','清甜','控制热量',12,2,360,18,12,44,SHA2('香蕉鸡蛋饼|香蕉|鸡蛋|面粉',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
(@x12,@recipe_source,'builtin-barley-congee','1','CURATED','红豆薏米粥','谷物豆类搭配，饱腹且有膳食纤维。','家常菜','清甜','均衡',45,3,540,18,6,96,SHA2('红豆薏米粥|红豆|薏米|大米',256),'APPROVED','鲜知内置营养与做法数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));
INSERT INTO recipe_component (id,recipe_id,name,role,quantity,unit,scaling_rule,minimum_quantity,maximum_quantity,sort_order) VALUES
(UUID_TO_BIN(UUID()),@x1,'西兰花','PRIMARY',300,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@x1,'蒜','SEASONING',15,'g','BOUNDED',8,25,2),(UUID_TO_BIN(UUID()),@x1,'食用油','SEASONING',8,'ml','BOUNDED',4,15,3),
(UUID_TO_BIN(UUID()),@x2,'鸡胸肉','PRIMARY',280,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@x2,'香菇','PRIMARY',150,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@x2,'生抽','SEASONING',12,'ml','BOUNDED',6,20,3),
(UUID_TO_BIN(UUID()),@x3,'三文鱼','PRIMARY',150,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@x3,'牛油果','PRIMARY',1,'piece','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@x3,'生菜','SIDE',80,'g','LINEAR',NULL,NULL,3),
(UUID_TO_BIN(UUID()),@x4,'金枪鱼','PRIMARY',160,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@x4,'玉米','SIDE',100,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@x4,'米饭','PRIMARY',300,'g','LINEAR',NULL,NULL,3),
(UUID_TO_BIN(UUID()),@x5,'紫菜','PRIMARY',10,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@x5,'鸡蛋','PRIMARY',2,'piece','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@x5,'葱','SEASONING',10,'g','BOUNDED',5,18,3),
(UUID_TO_BIN(UUID()),@x6,'冬瓜','PRIMARY',400,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@x6,'虾皮','PRIMARY',30,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@x6,'姜','SEASONING',8,'g','BOUNDED',4,15,3),
(UUID_TO_BIN(UUID()),@x7,'茄子','PRIMARY',300,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@x7,'豆角','PRIMARY',180,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@x7,'蒜','SEASONING',12,'g','BOUNDED',6,20,3),
(UUID_TO_BIN(UUID()),@x8,'土豆','PRIMARY',350,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@x8,'醋','SEASONING',15,'ml','BOUNDED',8,25,2),(UUID_TO_BIN(UUID()),@x8,'干辣椒','SEASONING',5,'g','BOUNDED',2,8,3),
(UUID_TO_BIN(UUID()),@x9,'牛肉','PRIMARY',220,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@x9,'菠菜','SIDE',100,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@x9,'胡萝卜','SIDE',80,'g','LINEAR',NULL,NULL,3),(UUID_TO_BIN(UUID()),@x9,'米饭','PRIMARY',300,'g','LINEAR',NULL,NULL,4),
(UUID_TO_BIN(UUID()),@x10,'牛肉','PRIMARY',220,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@x10,'生菜','SIDE',100,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@x10,'薄饼','PRIMARY',4,'piece','LINEAR',NULL,NULL,3),
(UUID_TO_BIN(UUID()),@x11,'香蕉','PRIMARY',2,'piece','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@x11,'鸡蛋','PRIMARY',2,'piece','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@x11,'面粉','PRIMARY',80,'g','LINEAR',NULL,NULL,3),
(UUID_TO_BIN(UUID()),@x12,'红豆','PRIMARY',80,'g','LINEAR',NULL,NULL,1),(UUID_TO_BIN(UUID()),@x12,'薏米','PRIMARY',80,'g','LINEAR',NULL,NULL,2),(UUID_TO_BIN(UUID()),@x12,'大米','PRIMARY',80,'g','LINEAR',NULL,NULL,3);
INSERT INTO recipe_step (id,recipe_id,step_number,instruction_text) VALUES
(UUID_TO_BIN(UUID()),@x1,1,'西兰花焯水后沥干。'),(UUID_TO_BIN(UUID()),@x1,2,'蒜末炒香，加入西兰花快速翻炒。'),
(UUID_TO_BIN(UUID()),@x2,1,'鸡肉和香菇切丁。'),(UUID_TO_BIN(UUID()),@x2,2,'鸡肉炒熟后加入香菇和生抽。'),
(UUID_TO_BIN(UUID()),@x3,1,'三文鱼煎熟后切块。'),(UUID_TO_BIN(UUID()),@x3,2,'牛油果、生菜和鱼肉装碗即可。'),
(UUID_TO_BIN(UUID()),@x4,1,'金枪鱼沥油，与玉米和米饭拌匀。'),(UUID_TO_BIN(UUID()),@x4,2,'捏成饭团或装入便当盒。'),
(UUID_TO_BIN(UUID()),@x5,1,'水开后放紫菜。'),(UUID_TO_BIN(UUID()),@x5,2,'淋入蛋液，撒葱花调味。'),
(UUID_TO_BIN(UUID()),@x6,1,'冬瓜切片，加水煮至半透明。'),(UUID_TO_BIN(UUID()),@x6,2,'加入虾皮和姜丝再煮五分钟。'),
(UUID_TO_BIN(UUID()),@x7,1,'茄子和豆角切段。'),(UUID_TO_BIN(UUID()),@x7,2,'依次下锅炒熟，蒜末调味。'),
(UUID_TO_BIN(UUID()),@x8,1,'土豆切丝并冲洗淀粉。'),(UUID_TO_BIN(UUID()),@x8,2,'大火快炒，最后加醋和辣椒。'),
(UUID_TO_BIN(UUID()),@x9,1,'牛肉炒熟，菠菜和胡萝卜分别焯熟。'),(UUID_TO_BIN(UUID()),@x9,2,'所有食材铺在米饭上，拌匀食用。'),
(UUID_TO_BIN(UUID()),@x10,1,'牛肉煎熟切条，生菜洗净。'),(UUID_TO_BIN(UUID()),@x10,2,'用薄饼包入食材卷起。'),
(UUID_TO_BIN(UUID()),@x11,1,'香蕉压泥，与鸡蛋和面粉拌匀。'),(UUID_TO_BIN(UUID()),@x11,2,'平底锅小火煎至两面金黄。'),
(UUID_TO_BIN(UUID()),@x12,1,'红豆和薏米提前浸泡。'),(UUID_TO_BIN(UUID()),@x12,2,'与大米一起煮至软烂。');
INSERT INTO recipe_knowledge_chunk (id,recipe_id,chunk_type,content_text,source_version,attribution_text,index_status,created_at,updated_at)
SELECT UUID_TO_BIN(UUID()),id,'SUMMARY',CONCAT(title,' ',COALESCE(summary,'')),'1','鲜知内置营养与做法数据','MYSQL_INDEXED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)
FROM recipe WHERE source_id=@recipe_source AND source_recipe_id IN ('builtin-garlic-broccoli','builtin-mushroom-chicken','builtin-salmon-avocado','builtin-tuna-corn-rice','builtin-seaweed-soup','builtin-wintermelon-shrimp','builtin-eggplant-beans','builtin-hot-sour-potato','builtin-korean-bibimbap','builtin-beef-wrap','builtin-banana-pancake','builtin-barley-congee');
INSERT INTO recipe_search_index_state (recipe_id,mysql_indexed_at,status,updated_at)
SELECT r.id,UTC_TIMESTAMP(3),'MYSQL_ONLY',UTC_TIMESTAMP(3) FROM recipe r LEFT JOIN recipe_search_index_state s ON s.recipe_id=r.id
WHERE r.review_status='APPROVED' AND s.recipe_id IS NULL;
