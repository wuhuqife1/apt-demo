package processor;

import annotation.Factory;
import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@AutoService(Processor.class) // 生成META-INF/services/javax.annotation.processing.Processor文件
public class FactoryProcessor extends AbstractProcessor {
    private Types typeUtils;
    private Elements elementUtils;
    private Filer filer;
    private Messager messager;
    private Map<String, FactoryGroupedClasses> factoryClasses = new LinkedHashMap<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotataions = new LinkedHashSet<>();
        annotataions.add(Factory.class.getCanonicalName());
        return annotataions;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        // 用来处理TypeMirror的工具类，可以从Element:TypeElement中获取类的名字，但是获取不到类的信息，例如它的父类。
        // 这种信息需要通过TypeMirror获取
        // 可以通过调用elements.asType()获取元素的TypeMirror。
        typeUtils = processingEnv.getTypeUtils();
        // 用来处理Element的工具类，即源码：包含程序的元素，例如包、类或者方法
        elementUtils = processingEnv.getElementUtils();
        // 使用Filer可以创建文件
        filer = processingEnv.getFiler();
        // 提供给注解处理器一个报告错误、警告以及提示信息的途径
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            // 1. 遍历所有被注解了@Factory的元素：可以是类、方法、变量等
            for (Element element : roundEnv.getElementsAnnotatedWith(Factory.class)) {
                // 2. 检查被注解为@Factory的元素是否是一个类：要确保只有class元素才被处理器处理
                if (!element.getKind().equals(ElementKind.CLASS)) {
                    // 如果不是类，抛出错误
                    throw new ProcessingException(element, "Only classes can be annotated with @%s",
                            Factory.class.getSimpleName()); // 退出处理
                }

                // 3. 检查被注解的类必须有只要一个公开的构造函数，不是抽象类，继承于特定的类型，以及是一个公开类
                TypeElement typeElement = (TypeElement) element;
                FactoryAnnotatedClass annotatedClass = new FactoryAnnotatedClass(typeElement); // throws IllegalArgumentException
                if (!isValidClass(annotatedClass)) {
                    return true; // 已经打印了错误信息，退出处理过程
                }

                // 4. 如果校验类成功，添加FactoryAnnotatedClass到对应的FactoryGroupedClasses中
                FactoryGroupedClasses factoryClass = factoryClasses.get(annotatedClass.getQualifiedFactoryGroupName());
                if (factoryClass == null) {
                    String qualifiedGroupName = annotatedClass.getQualifiedFactoryGroupName();
                    factoryClass = new FactoryGroupedClasses(qualifiedGroupName);
                    factoryClasses.put(qualifiedGroupName, factoryClass);
                }
                factoryClass.add(annotatedClass);
            }

            // 5. 已经收集了所有的被@Factory注解的类保存到为FactoryAnnotatedClass，并且组合到了FactoryGroupedClasses，可以为每个工厂生成Java文件了
            for (FactoryGroupedClasses factoryClass : factoryClasses.values()) {
                // 为每个工厂生成Java文件
                factoryClass.generateCode(elementUtils, filer);
            }
            // 注解处理过程是需要经过多轮处理的，如果不清除factoryClasses，在第二轮的process()中，仍然保存着第一轮的数据，并且会尝试生成在第一轮中已经生成的文件
            factoryClasses.clear();
        } catch (ProcessingException e) {
            error(e.getElement(), e.getMessage());
        } catch (IOException e) {
            error(null, e.getMessage());
        }

        return true;
    }

    /**
     * 检查是否满足所有的规则：
     * 1.必须是公开类：classElement.getModifiers().contains(Modifier.PUBLIC)
     * 2.必须是非抽象类：classElement.getModifiers().contains(Modifier.ABSTRACT)
     * 3.必须是@Factory.type()指定的类型的子类或者接口的实现：
     * 首先我们使用elementUtils.getTypeElement(item.getQualifiedFactoryGroupName())创建一个传入的Class(@Factory.type())的元素。
     * 是的，你可以仅仅通过已知的合法类名来直接创建TypeElement（使用TypeMirror）。
     * 接下来我们检查它是一个接口还是一个类：superClassElement.getKind() == ElementKind.INTERFACE。所以我们这里有两种情况：
     * 如果是接口，就判断classElement.getInterfaces().contains(superClassElement.asType())；
     * 如果是类，我们就必须使用currentClass.getSuperclass()扫描继承层级。注意，整个检查也可以使用typeUtils.isSubtype()来实现。
     * 4.类必须有一个公开的默认构造函数：
     * 遍历所有的闭元素classElement.getEnclosedElements()，
     * 然后检查ElementKind.CONSTRUCTOR、Modifier.PUBLIC以及constructorElement.getParameters().size() == 0
     */
    private boolean isValidClass(FactoryAnnotatedClass item) throws ProcessingException {

        // 转换为TypeElement, 含有更多特定的方法
        TypeElement classElement = item.getTypeElement();

        // 检查是否是一个公开类
        if (!classElement.getModifiers().contains(Modifier.PUBLIC)) {
            error(classElement, "The class %s is not public.",
                    classElement.getQualifiedName().toString());
            return false;
        }

        // 检查是否是一个抽象类
        if (classElement.getModifiers().contains(Modifier.ABSTRACT)) {
            error(classElement, "The class %s is abstract. You can't annotate abstract classes with @%",
                    classElement.getQualifiedName().toString(), Factory.class.getSimpleName());
            return false;
        }

        // 检查继承关系: 必须是@Factory.type()指定的类型子类
        TypeElement superClassElement = elementUtils.getTypeElement(item.getQualifiedFactoryGroupName());
        if (superClassElement.getKind() == ElementKind.INTERFACE) {
            // 检查接口是否实现了
            if (!classElement.getInterfaces().contains(superClassElement.asType())) {
                error(classElement, "The class %s annotated with @%s must implement the interface %s",
                        classElement.getQualifiedName().toString(), Factory.class.getSimpleName(),
                        item.getQualifiedFactoryGroupName());
                return false;
            }
        } else {
            // 检查子类
            TypeElement currentClass = classElement;
            while (true) {
                TypeMirror superClassType = currentClass.getSuperclass();

                if (superClassType.getKind() == TypeKind.NONE) {
                    // 到达了基本类型(java.lang.Object), 所以退出
                    error(classElement, "The class %s annotated with @%s must inherit from %s",
                            classElement.getQualifiedName().toString(), Factory.class.getSimpleName(),
                            item.getQualifiedFactoryGroupName());
                    return false;
                }

                if (superClassType.toString().equals(item.getQualifiedFactoryGroupName())) {
                    // 找到了要求的父类
                    break;
                }

                // 在继承树上继续向上搜寻
                currentClass = (TypeElement) typeUtils.asElement(superClassType);
            }
        }

        // 检查是否提供了默认公开构造函数
        for (Element enclosed : classElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement constructorElement = (ExecutableElement) enclosed;
                if (constructorElement.getParameters().size() == 0 && constructorElement.getModifiers()
                        .contains(Modifier.PUBLIC)) {
                    // 找到了默认构造函数
                    return true;
                }
            }
        }

        // 没有找到默认构造函数
        throw new ProcessingException(classElement,
                "The class %s must provide an public empty default constructor",
                classElement.getQualifiedName().toString());
    }

    private void error(Element e, String msg, Object... args) {
        messager.printMessage(
                Diagnostic.Kind.ERROR,
                String.format(msg, args),
                e);
    }
}
