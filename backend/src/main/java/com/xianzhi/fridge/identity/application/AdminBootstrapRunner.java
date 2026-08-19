package com.xianzhi.fridge.identity.application;

import com.xianzhi.fridge.identity.domain.UserRole;
import com.xianzhi.fridge.identity.infrastructure.AppUser;
import com.xianzhi.fridge.identity.infrastructure.AppUserRepository;
import com.xianzhi.fridge.shared.application.AuditService;
import com.xianzhi.fridge.shared.config.AppProperties;
import com.xianzhi.fridge.shared.domain.Hashing;
import com.xianzhi.fridge.shared.domain.UuidV7;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Component
@Profile("admin-bootstrap")
@Order(Ordered.LOWEST_PRECEDENCE)
public class AdminBootstrapRunner implements ApplicationRunner {
    private static final String ALPHABET="ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private final AppUserRepository users;private final PasswordEncoder passwords;private final AppProperties properties;
    private final AuditService audit;private final Environment environment;private final TransactionTemplate transactions;
    private final ConfigurableApplicationContext context;
    public AdminBootstrapRunner(AppUserRepository users,PasswordEncoder passwords,AppProperties properties,
            AuditService audit,Environment environment,PlatformTransactionManager transactionManager,
            ConfigurableApplicationContext context){this.users=users;this.passwords=passwords;
        this.properties=properties;this.audit=audit;this.environment=environment;
        this.transactions=new TransactionTemplate(transactionManager);this.context=context;}
    @Override public void run(ApplicationArguments args){
        transactions.executeWithoutResult(status->bootstrap());
        context.close();
    }
    private void bootstrap(){
        if(users.findAll().stream().anyMatch(value->value.getRole()==UserRole.ADMIN&&value.getDeletedAt()==null)){
            throw new IllegalStateException("An administrator already exists and was not modified");
        }
        if(users.findByUsername("admin").isPresent())throw new IllegalStateException("Username admin already exists and was not modified");
        String email=environment.getProperty("ADMIN_BOOTSTRAP_EMAIL");
        if(email==null||email.isBlank()||!email.contains("@"))throw new IllegalStateException("ADMIN_BOOTSTRAP_EMAIL is required");
        String raw=randomPassword();String passwordHash=passwords.encode(raw);Instant now=Instant.now();AppUser admin=new AppUser(UuidV7.next(),"admin",email.trim().toLowerCase(),
                passwordHash,"系统管理员",properties.getTimezone());admin.changeRole(UserRole.ADMIN);
        admin.requireTemporaryPassword(passwordHash,now.plus(Duration.ofHours(24)),Hashing.sha256("bootstrap:"+admin.getId()));users.saveAndFlush(admin);
        audit.record(admin.getId(),admin.getId(),"ADMIN_BOOTSTRAPPED",Map.of("expiresAt",admin.getTemporaryPasswordExpiresAt().toString()));
        System.out.println("ADMIN_BOOTSTRAP_USERNAME=admin");System.out.println("ADMIN_BOOTSTRAP_TEMPORARY_PASSWORD="+raw);
        System.out.println("ADMIN_BOOTSTRAP_PASSWORD_EXPIRES_AT="+admin.getTemporaryPasswordExpiresAt());
    }
    private static String randomPassword(){SecureRandom random=new SecureRandom();StringBuilder value=new StringBuilder(24);value.append("ABCDEFGHJKLMNPQRSTUVWXYZ".charAt(random.nextInt(24)));value.append("abcdefghijkmnopqrstuvwxyz".charAt(random.nextInt(24)));value.append("23456789".charAt(random.nextInt(8)));value.append("!@#$%".charAt(random.nextInt(5)));while(value.length()<24)value.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));for(int i=value.length()-1;i>0;i--){int swap=random.nextInt(i+1);char c=value.charAt(i);value.setCharAt(i,value.charAt(swap));value.setCharAt(swap,c);}return value.toString();}
}
