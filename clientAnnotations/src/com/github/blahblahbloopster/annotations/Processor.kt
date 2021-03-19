package com.github.blahblahbloopster.annotations

import com.github.blahblahbloopster.CustomGetterSetter
import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import kotlin.reflect.jvm.internal.impl.builtins.jvm.JavaToKotlinClassMap
import kotlin.reflect.jvm.internal.impl.name.FqName

@AutoService(Processor::class) // For registering the service
@SupportedSourceVersion(SourceVersion.RELEASE_14) // to support Java 8
@SupportedOptions(GetterSetterProcessor.KAPT_KOTLIN_GENERATED_OPTION_NAME)
@SupportedAnnotationTypes("com.github.blahblahbloopster.CustomGetterSetter")
class GetterSetterProcessor : AbstractProcessor() {

    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
        var done = false
    }

    // from https://github.com/square/kotlinpoet/issues/236#issuecomment-555924117
    private fun TypeName.javaToKotlinType(): TypeName {
        return when (this) {
            is ParameterizedTypeName -> {
                (rawType.javaToKotlinType() as ClassName).parameterizedBy(*(typeArguments.map { it.javaToKotlinType() }.toTypedArray()))
            }
            is WildcardTypeName -> {
                outTypes[0].javaToKotlinType()
            }
            else -> {
                val className = JavaToKotlinClassMap.INSTANCE.mapJavaToKotlin(FqName(toString()))?.asSingleFqName()?.asString()
                return if (className == null) {
                    this
                } else {
                    ClassName.bestGuess(className)
                }
            }
        }
    }

    fun Element.javaToKotlinType(): TypeName = asType().asTypeName().javaToKotlinType()


    @Synchronized
    override fun process(items: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if (done) return true
        done = true
        val output = TypeSpec.objectBuilder("VarsImpl")
        output.addSuperinterface(ClassName("mindustry.client", "ClientVars"))
        roundEnv.getElementsAnnotatedWith(CustomGetterSetter::class.java).forEach { methodElement ->
            if (methodElement.kind != ElementKind.FIELD) {
                return@forEach
            }

            val annotation = methodElement.getAnnotation(CustomGetterSetter::class.java)
            val variable = methodElement as VariableElement

            val getter = FunSpec.builder(annotation.getterName)
            getter.addModifiers(KModifier.OVERRIDE)
            // asTypeName() is deprecated, but I don't see another option
            getter.returns(variable.javaToKotlinType())
            getter.addCode(CodeBlock.of("return com.github.blahblahbloopster.ClientVarsImpl.${variable.simpleName}"))
            output.addFunction(getter.build())

            if (annotation.setterName == "") return@forEach
            val setter = FunSpec.builder(annotation.setterName)
            setter.addModifiers(KModifier.OVERRIDE)
            setter.addParameter(ParameterSpec.builder(variable.simpleName.toString(), variable.javaToKotlinType()).build())
            setter.addCode(CodeBlock.of("com.github.blahblahbloopster.ClientVarsImpl.${variable.simpleName} = ${variable.simpleName}"))
            output.addFunction(setter.build())
        }
        val built = output.build()
        val fileBuilder = FileSpec.builder("com.github.blahblahbloopster.gen", "VarsImpl").addType(built)

//        // janky way of removing java.lang.String from imports
//        val imports = fileBuilder.imports.filter { !it.qualifiedName.contains("String") }
//        fileBuilder.clearImports()
//        for (item in imports) {
//            fileBuilder.addImport(item)
//        }
//        fileBuilder.addImport("kotlin", "String")
        fileBuilder.build().writeTo(processingEnv.filer)
        return true
    }
}
