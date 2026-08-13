// 食材名称 -> 图片资源路径（public 下）
// 未收录的食材会返回 null，界面将以文字显示
const FOOD_IMAGES: Record<string, string> = {
  // ---- 蔬菜 ----
  番茄: '/Green/Green/tomato.png',
  圣女果: '/Green/Green/cherry_tomatoes.png',
  土豆: '/Green/Green/potato.png',
  胡萝卜: '/Green/Green/carrot.png',
  青椒: '/Green/Green/bell_peppers.png',
  彩椒: '/Green/Green/bell_peppers.png',
  黄瓜: '/Green/Green/cucumber.png',
  西葫芦: '/Green/Green/zucchini.png',
  白菜: '/Green/Green/napa_cabbage.png',
  圆白菜: '/Green/Green/round_cabbage.png',
  卷心菜: '/Green/Green/cabbage.png',
  菠菜: '/Green/Green/spinach.png',
  西兰花: '/Green/Green/broccoli.png',
  花菜: '/Green/Green/cauliflower.png',
  生菜: '/Green/Green/lettuce.png',
  洋葱: '/Green/Green/onion.png',
  红洋葱: '/Green/Green/red_onion.png',
  蒜: '/Green/Green/garlic.png',
  大蒜: '/Green/Green/garlic.png',
  姜: '/Condiment/Condiment/ginger.png',
  葱: '/Green/Green/green_onion.png',
  大葱: '/Green/Green/green_onion.png',
  蘑菇: '/Green/Green/mushroom.png',
  香菇: '/Green/Green/shiitake_mushroom.png',
  玉米: '/Green/Green/corn.png',
  茄子: '/Green/Green/eggplant_long.png',
  南瓜: '/Green/Green/pumpkin.png',
  红薯: '/Green/Green/sweet_potato.png',
  萝卜: '/Green/Green/radish.png',
  白萝卜: '/Green/Green/radish.png',
  芹菜: '/Green/Green/celery.png',
  豆芽: '/Green/Green/bean_sprouts.png',
  豆角: '/Green/Green/green_beans.png',
  苦瓜: '/Green/Green/bitter_melon.png',
  上海青: '/Green/Green/shanghai_bok_choy.png',
  青菜: '/Green/Green/bok_choy.png',
  芦笋: '/Green/Green/asparagus.png',
  牛油果: '/Green/Green/avocado.png',
  甜菜: '/Green/Green/beetroot.png',
  冬瓜: '/Green/Green/winter_melon.png',
  香菜: '/Green/Green/cilantro.png',

  // ---- 水果 ----
  苹果: '/Free_pixel_food_16x16/Free_pixel_food_16x16/Icons/fruit_apple.png',
  香蕉: '/Free_pixel_food_16x16/Free_pixel_food_16x16/Icons/fruit_banana.png',
  蓝莓: '/Free_pixel_food_16x16/Free_pixel_food_16x16/Icons/fruit_blueberry.png',
  樱桃: '/Free_pixel_food_16x16/Free_pixel_food_16x16/Icons/fruit_cherry.png',
  葡萄: '/Free_pixel_food_16x16/Free_pixel_food_16x16/Icons/fruit_grape_red.png',
  青提: '/Free_pixel_food_16x16/Free_pixel_food_16x16/Icons/fruit_greengrape.png',
  猕猴桃: '/Free_pixel_food_16x16/Free_pixel_food_16x16/Icons/fruit_kiwi.png',
  柠檬: '/Free_pixel_food_16x16/Free_pixel_food_16x16/Icons/fruit_lemon.png',
  青柠: '/Free_pixel_food_16x16/Free_pixel_food_16x16/Icons/fruit_lime.png',
  橙子: '/Free_pixel_food_16x16/Free_pixel_food_16x16/Icons/fruit_orange.png',
  桃子: '/Free_pixel_food_16x16/Free_pixel_food_16x16/Icons/fruit_peach.png',
  草莓: '/Free_pixel_food_16x16/Free_pixel_food_16x16/Icons/fruit_strawberry.png',
  西瓜: '/Free_pixel_food_16x16/Free_pixel_food_16x16/Icons/fruit_watermelon.png',
  芒果: '/Green/Green/mango.png',

  // ---- 蛋奶 ----
  鸡蛋: '/Free_pixel_food_16x16/Free_pixel_food_16x16/Icons/eggs_white.png',
  牛奶: '/Condiment/Condiment/milk.png',
  酸奶: '/Condiment/Condiment/sour_cream.png',
  黄油: '/Condiment/Condiment/butter.png',
  奶酪: '/Carb/Carb/cheese_wheel.png',
  芝士: '/Free_pixel_food_16x16/Free_pixel_food_16x16/Icons/cheese_gouda.png',

  // ---- 肉类 ----
  猪肉: '/food_processed/meats/pork_chop.png',
  牛肉: '/food_processed/meats/beef_tenderloin.png',
  鸡肉: '/food_processed/meats/chicken_leg.png',
  鸡腿: '/food_processed/meats/chicken_leg.png',
  鸡胸肉: '/food_processed/meats/chicken_breast.png',
  鸡翅: '/food_processed/meats/roast_chicken.png',
  羊肉: '/food_processed/meats/lamb_shank.png',
  鱼肉: '/food_processed/meats/fish_slice.png',
  鱼: '/food_processed/meats/fish_slice.png',
  三文鱼: '/food_processed/meats/salmon_fillet.png',
  虾: '/food_processed/meats/shrimp_tempura.png',
  螃蟹: '/food_processed/meats/crab_leg.png',
  龙虾: '/food_processed/meats/lobster.png',
  火腿: '/food_processed/meats/ham.png',
  香肠: '/food_processed/meats/sausage_links.png',
  培根: '/food_processed/meats/bacon.png',
  肉丸: '/food_processed/meats/meatball.png',
  腊肉: '/food_processed/meats/jerky.png',
  烤鸡: '/food_processed/meats/roast_chicken.png',
  牛排: '/food_processed/meats/tbone_steak.png',
  寿司: '/food_processed/meats/sushi.png',

  // ---- 主食 ----
  大米: '/Carb/Carb/white_rice.png',
  米饭: '/Carb/Carb/fried_rice.png',
  面条: '/Carb/Carb/spaghetti.png',
  拉面: '/Carb/Carb/ramen.png',
  面包: '/Carb/Carb/whole_wheat_bread.png',
  吐司: '/Carb/Carb/toast.png',
  法棍: '/Carb/Carb/baguette.png',
  牛角包: '/Carb/Carb/croissant.png',
  贝果: '/Carb/Carb/bagel.png',
  意面: '/Carb/Carb/penne.png',
  面粉: '/Carb/Carb/flour.png',
  淀粉: '/Carb/Carb/flour.png',
  松饼: '/Carb/Carb/pancake.png',
  麦片: '/Carb/Carb/oatmeal_cookie.png',
  玉米面: '/Carb/Carb/cornmeal.png',

  // ---- 调味品 ----
  盐: '/Condiment/Condiment/salt.png',
  白糖: '/Condiment/Condiment/sugar.png',
  冰糖: '/Condiment/Condiment/sugar_cubes.png',
  红糖: '/Condiment/Condiment/brown_sugar.png',
  食用油: '/Condiment/Condiment/cooking_oil.png',
  橄榄油: '/Condiment/Condiment/olive_oil.png',
  香油: '/Condiment/Condiment/sesame_oil.png',
  生抽: '/Condiment/Condiment/light_soy_sauce.png',
  老抽: '/Condiment/Condiment/dark_soy_sauce.png',
  酱油: '/Condiment/Condiment/soy_sauce.png',
  醋: '/Condiment/Condiment/vinegar.png',
  蜂蜜: '/Condiment/Condiment/honey.png',
  番茄酱: '/Condiment/Condiment/ketchup_bottle.png',
  辣椒酱: '/Condiment/Condiment/chili_sauce_jar.png',
  芥末: '/Condiment/Condiment/mustard.png',
  咖喱粉: '/Condiment/Condiment/curry_powder.png',
  黑胡椒: '/Condiment/Condiment/peppercorns.png',
  花生酱: '/Condiment/Condiment/peanut_butter.png',
  蛋黄酱: '/Condiment/Condiment/mayonnaise.png',
  蚝油: '/Condiment/Condiment/oyster_sauce.png',
  味噌: '/Condiment/Condiment/miso_paste.png',
  芝麻酱: '/Condiment/Condiment/sesame_paste.png',
  沙拉酱: '/Condiment/Condiment/salad_dressing.png',
  孜然: '/Condiment/Condiment/peppercorns.png',
  花椒: '/Condiment/Condiment/peppercorns.png',
  八角: '/Green/Green/star_anise.png',
  肉桂: '/Green/Green/cinnamon_stick.png',

  // ---- 饮料/零食 ----
  橙汁: '/Condiment/Condiment/orange_juice.png',
  果汁: '/Condiment/Condiment/mixed_juice.png',
  啤酒: '/Condiment/Condiment/beer.png',
  咖啡: '/Condiment/Condiment/coffee.png',
  可乐: '/Condiment/Condiment/lemonade.png',
  薯片: '/Carb/Carb/potato_chips.png',
  饼干: '/Carb/Carb/cookie.png',
  蛋糕: '/Free_pixel_food_16x16/Free_pixel_food_16x16/Icons/cake_strawberry.png',
  冰激凌: '/Free_pixel_food_16x16/Free_pixel_food_16x16/Icons/icecream_2scoops.png',

  // ---- 分类兜底 ----
  蔬菜: '/Green/Green/broccoli.png',
  肉类: '/food_processed/meats/raw_beef.png',
  水果: '/Free_pixel_food_16x16/Free_pixel_food_16x16/Icons/fruit_apple.png',
  蛋奶: '/Free_pixel_food_16x16/Free_pixel_food_16x16/Icons/eggs_white.png',
  水产: '/food_processed/meats/fish_slice.png',
  豆制品: '/Green/Green/black_beans.png',
  主食: '/Carb/Carb/white_rice.png',
  零食: '/Carb/Carb/cookie.png',
  饮料: '/Condiment/Condiment/orange_juice.png',
  调味品: '/Condiment/Condiment/soy_sauce.png',
}

export function findFoodImage(name: string): string | null {
  const n = (name || '').trim()
  if (!n) {
    return null
  }
  if (FOOD_IMAGES[n]) {
    return FOOD_IMAGES[n]
  }
  for (const key of Object.keys(FOOD_IMAGES)) {
    if (n.includes(key) || key.includes(n)) {
      return FOOD_IMAGES[key]
    }
  }
  return null
}
