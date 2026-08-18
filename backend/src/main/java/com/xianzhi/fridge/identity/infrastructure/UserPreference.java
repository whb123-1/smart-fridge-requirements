package com.xianzhi.fridge.identity.infrastructure;

import jakarta.persistence.*;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="user_preference")
public class UserPreference {
    @Id @JdbcTypeCode(Types.BINARY) @Column(name="user_id") private UUID userId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="json") private String tastes;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="json") private String cuisines;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="json") private String allergies;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="json") private String dislikes;
    @Column(name="dietary_goal",length=32) private String dietaryGoal;
    @Column(name="calorie_target") private Integer calorieTarget;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version private long version;
    protected UserPreference() { }
    public UserPreference(UUID userId,Instant now){this.userId=userId;this.tastes=this.cuisines=this.allergies=this.dislikes="[]";this.createdAt=this.updatedAt=now;}
    public void update(String tastes,String cuisines,String allergies,String dislikes,String goal,Integer calories,Instant now){this.tastes=tastes;this.cuisines=cuisines;this.allergies=allergies;this.dislikes=dislikes;this.dietaryGoal=goal;this.calorieTarget=calories;this.updatedAt=now;}
    public String getTastes(){return tastes;} public String getCuisines(){return cuisines;} public String getAllergies(){return allergies;} public String getDislikes(){return dislikes;} public String getDietaryGoal(){return dietaryGoal;} public Integer getCalorieTarget(){return calorieTarget;}
}
