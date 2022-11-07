package processor;

import annotation.Factory;
import org.apache.commons.lang3.StringUtils;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;

/**
 * 保存被注解类的数据，比如合法的类的名字，以及@Factory注解本身的一些信息
 */
public class FactoryAnnotatedClass {

    private TypeElement annotatedClassElement;
    private String qualifiedSuperClassName;
    private String simpleTypeName;
    private String id;

    public FactoryAnnotatedClass(TypeElement classElement) throws IllegalArgumentException {
        this.annotatedClassElement = classElement;
        Factory annotation = classElement.getAnnotation(Factory.class);
        id = annotation.id();

        if (StringUtils.isEmpty(id)) {
            throw new IllegalArgumentException(
                    String.format("id() in @%s for class %s is null or empty! that's not allowed",
                            Factory.class.getSimpleName(), classElement.getQualifiedName().toString()));
        }

        // 获取全类名
        try {
            // 这里的类型是一个java.lang.Class。这就意味着，他是一个真正的Class对象。因为注解处理是在编译Java源代码之前
            Class<?> clazz = annotation.type();
            // 1)如果这个类已经被编译：这种情况是：如果第三方.jar包含已编译的被@Factory注解.class文件。在这种情况下，可以直接获取Class
            qualifiedSuperClassName = clazz.getCanonicalName();
            simpleTypeName = clazz.getSimpleName();
        } catch (MirroredTypeException mte) {
            /*
             * 2)这个还没有被编译：这种情况是我们尝试编译被@Fractory注解的源代码。这种情况下，直接获取Class会抛出MirroredTypeException异常
             *   MirroredTypeException包含一个TypeMirror，它表示我们未编译类。
             *   因为已经知道它必定是一个类类型（已经在前面检查过），可以直接强制转换为DeclaredType，然后读取TypeElement来获取合法的名字
             */
            DeclaredType classTypeMirror = (DeclaredType) mte.getTypeMirror();
            TypeElement classTypeElement = (TypeElement) classTypeMirror.asElement();
            qualifiedSuperClassName = classTypeElement.getQualifiedName().toString();
            simpleTypeName = classTypeElement.getSimpleName().toString();
        }
    }

    /**
     * 获取在{@link Factory#id()}中指定的id
     * return the id
     */
    public String getId() {
        return id;
    }

    /**
     * 获取在{@link Factory#type()}指定的类型合法全名
     *
     * @return qualified name
     */
    public String getQualifiedFactoryGroupName() {
        return qualifiedSuperClassName;
    }


    /**
     * 获取在 {@link Factory#type()} 中指定的类型的简单名字
     *
     * @return qualified name
     */
    public String getSimpleFactoryGroupName() {
        return simpleTypeName;
    }

    /**
     * 获取被@Factory注解的原始元素
     */
    public TypeElement getTypeElement() {
        return annotatedClassElement;
    }
}