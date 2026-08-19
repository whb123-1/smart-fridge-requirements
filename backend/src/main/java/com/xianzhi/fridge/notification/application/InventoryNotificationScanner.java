package com.xianzhi.fridge.notification.application;

import com.xianzhi.fridge.identity.infrastructure.AppUserRepository;
import com.xianzhi.fridge.inventory.application.InventoryService;
import com.xianzhi.fridge.inventory.domain.AssessmentStatus;
import com.xianzhi.fridge.notification.domain.NotificationType;
import org.springframework.stereotype.Service;

@Service
public class InventoryNotificationScanner {
    private final AppUserRepository users;private final InventoryService inventory;private final NotificationService notifications;
    public InventoryNotificationScanner(AppUserRepository users,InventoryService inventory,NotificationService notifications){this.users=users;this.inventory=inventory;this.notifications=notifications;}
    public void scan(){for(var user:users.findAll()){if(user.getDeletedAt()!=null)continue;for(var due:inventory.expiry(user.getId(),null,null,3)){if(due.assessment()==null)continue;AssessmentStatus status=due.assessment().safetyStatus();if(status!=AssessmentStatus.EXPIRED&&status!=AssessmentStatus.EXPIRING_SOON)continue;NotificationType type=status==AssessmentStatus.EXPIRED?NotificationType.EXPIRED:NotificationType.EXPIRY_SOON;String key="expiry:"+due.batchId()+":"+due.assessment().id()+":"+type;notifications.ensure(user.getId(),type,"INVENTORY_BATCH",due.batchId(),key,status==AssessmentStatus.EXPIRED?"HIGH":"MEDIUM",due.itemName()+"需要处理",status==AssessmentStatus.EXPIRED?"该批次已超过估算期限，请检查后处理。":"该批次接近估算期限，建议优先处理。","/inventory?batchId="+due.batchId());}for(var item:inventory.listItems(user.getId(),null,null,null,null,null)){if(item.lowStock())notifications.ensure(user.getId(),NotificationType.LOW_STOCK,"INVENTORY_ITEM",item.id(),"low-stock:"+item.id(),"LOW",item.name()+"库存偏低","当前同单位库存已达到补货阈值。","/shopping?itemId="+item.id());}}}
}
