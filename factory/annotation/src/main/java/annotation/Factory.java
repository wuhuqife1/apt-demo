package annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 只有类可以被@Factory注解，因为接口或者抽象类并不能用new操作实例化；
 * 被@Factory注解的类，必须至少提供一个公开的默认构造器（即没有参数的构造函数）。否者我们没法实例化一个对象。
 * 被@Factory注解的类必须直接或者间接的继承于type()指定的类型；
 * 具有相同的type的注解类，将被聚合在一起生成一个工厂类。这个生成的类使用Factory后缀，例如type = Meal.class，将生成MealFactory工厂类；
 * id只能是String类型，并且在同一个type组中必须唯一。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface Factory {

    /**
     * 工厂的名字
     */
    Class<?> type();

    /**
     * 用来表示生成哪个对象的唯一id
     */
    String id();
}