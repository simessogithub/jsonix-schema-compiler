/**
 * Jsonix is a JavaScript library which allows you to convert between XML
 * and JavaScript object structures.
 *
 * Copyright (c) 2010 - 2014, Alexey Valikov, Highsource.org
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice, this
 *   list of conditions and the following disclaimer in the documentation and/or
 *   other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.hisrc.jsonix.compiler;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.hisrc.jscm.codemodel.JSCodeModel;
import org.hisrc.jscm.codemodel.expression.JSArrayLiteral;
import org.hisrc.jscm.codemodel.expression.JSAssignmentExpression;
import org.hisrc.jscm.codemodel.expression.JSObjectLiteral;
import org.hisrc.jscm.codemodel.impl.CodeModelImpl;
import org.hisrc.jsonix.xjc.customizations.PackageMapping;
import org.jvnet.jaxb2_commons.xml.bind.model.MClassInfo;
import org.jvnet.jaxb2_commons.xml.bind.model.MClassTypeInfo;
import org.jvnet.jaxb2_commons.xml.bind.model.MElementInfo;
import org.jvnet.jaxb2_commons.xml.bind.model.MEnumConstantInfo;
import org.jvnet.jaxb2_commons.xml.bind.model.MEnumLeafInfo;
import org.jvnet.jaxb2_commons.xml.bind.model.MModelInfo;
import org.jvnet.jaxb2_commons.xml.bind.model.MPackageInfo;
import org.jvnet.jaxb2_commons.xml.bind.model.MPackaged;
import org.jvnet.jaxb2_commons.xml.bind.model.MPropertyInfo;
import org.jvnet.jaxb2_commons.xml.bind.model.MTypeInfo;
import org.jvnet.jaxb2_commons.xml.bind.model.util.PackageInfoQNameAnalyzer;

public class JsonixCompiler<T, C extends T> {

	public static final String DEFAULT_SCOPED_NAME_DELIMITER = ".";

	final JSCodeModel codeModel = new CodeModelImpl();
	final Naming naming;

	private final MModelInfo<T, C> model;
	private final Map<String, PackageMapping> packageMappings;
	private final Map<String, JsonixModule> modules;

	public JsonixCompiler(final MModelInfo<T, C> model,
			Map<String, PackageMapping> packageMappings, Naming naming) {
		Validate.notNull(model);
		Validate.notNull(packageMappings);
		Validate.notNull(naming);
		this.model = model;
		this.packageMappings = new HashMap<String, PackageMapping>(
				packageMappings);
		this.naming = naming;
		this.modules = new HashMap<String, JsonixModule>(packageMappings.size());
	}

	JsonixModule getModule(final MPackaged packaged) {
		final MPackageInfo packageInfo = packaged.getPackageInfo();
		return getModule(packageInfo);
	}

	private JsonixModule getModule(final MPackageInfo packageInfo) {
		String packageName = packageInfo.getPackageName();
		final boolean blankPackage;
		if (StringUtils.isBlank(packageName)) {
			packageName = "";
			blankPackage = true;
		} else {
			blankPackage = false;
		}
		JsonixModule module = this.modules.get(packageName);
		if (module == null) {
			final PackageInfoQNameAnalyzer<T, C> analyzer = new PackageInfoQNameAnalyzer<T, C>(
					this.model);

			final String defaultElementNamespaceURI = analyzer
					.getMostUsedElementNamespaceURI(packageInfo);
			final String defaultAttributeNamespaceURI = analyzer
					.getMostUsedAttributeNamespaceURI(packageInfo);

			PackageMapping packageMapping = packageMappings.get(packageName);

			if (packageMapping == null) {

				packageMapping = new PackageMapping();
				packageMapping.setPackageName(packageName);
				packageMappings.put(packageName, packageMapping);
			}

			if (StringUtils.isBlank(packageMapping
					.getDefaultElementNamespaceURI())) {
				packageMapping
						.setDefaultElementNamespaceURI(defaultElementNamespaceURI);
			}

			if (StringUtils.isBlank(packageMapping
					.getDefaultAttributeNamespaceURI())) {
				packageMapping
						.setDefaultAttributeNamespaceURI(defaultAttributeNamespaceURI);
			}

			if (StringUtils.isBlank(packageMapping.getSpaceName())) {
				packageMapping.setSpaceName(blankPackage ? "generated"
						: packageName.replace('.', '_'));
			}
			if (packageMapping.getOutputPackageName() == null) {
				packageMapping.setOutputPackageName("");
			}

			if (packageMapping.getDirectory() == null) {
				packageMapping.setDirectory((blankPackage ? "" : packageMapping
						.getOutputPackageName().replace('.', '/')));
			}

			if (packageMapping.getFileName() == null) {
				packageMapping.setFileName(packageMapping.getSpaceName()
						+ ".js");

			}

			module = new JsonixModule(this.codeModel, this.naming,
					packageMapping);
			this.modules.put(packageName, module);
		}
		return module;
	}

	public Map<String, JsonixModule> compile() {
		compileClassInfos(model.getClassInfos());
		compileEnumLeafInfos(model.getEnumLeafInfos());
		compileElementInfos(model.getElementInfos());
		return this.modules;
	}

	public JSAssignmentExpression getTypeInfoDeclaration(
			MTypeInfo<T, C> typeInfo) {
		return typeInfo
				.acceptTypeInfoVisitor(new CreateTypeInfoDeclarationVisitor<T, C>(
						this, this.codeModel, this.naming));
	}

	public JSArrayLiteral compileClassInfos(
			Collection<MClassInfo<T, C>> classInfos) {
		final JSArrayLiteral classInfoMappings = this.codeModel.array();
		for (MClassInfo<T, C> classInfo : classInfos) {
			classInfoMappings.append(compileClassInfo(classInfo));
		}
		return classInfoMappings;
	}

	public JSArrayLiteral compileEnumLeafInfos(
			Collection<MEnumLeafInfo<T, C>> enumLeafInfos) {
		final JSArrayLiteral mappings = this.codeModel.array();
		for (MEnumLeafInfo<T, C> enumLeafInfo : enumLeafInfos) {
			mappings.append(compileEnumLeafInfo(enumLeafInfo));
		}
		return mappings;
	}

	public JSObjectLiteral compileClassInfo(MClassInfo<T, C> classInfo) {
		final JsonixModule module = getModule(classInfo);
		final JSObjectLiteral classInfoMapping = this.codeModel.object();
		classInfoMapping.append(naming.localName(), this.codeModel
				.string(classInfo
						.getContainerLocalName(DEFAULT_SCOPED_NAME_DELIMITER)));

		final MClassTypeInfo<T, C> baseTypeInfo = classInfo.getBaseTypeInfo();
		if (baseTypeInfo != null) {
			classInfoMapping.append(naming.baseTypeInfo(),
					getTypeInfoDeclaration(baseTypeInfo));
		}
		final JSArrayLiteral ps = compilePropertyInfos(classInfo);
		if (!ps.getElements().isEmpty()) {
			classInfoMapping.append(naming.propertyInfos(), ps);
		}
		module.registerTypeInfo(classInfoMapping);
		return classInfoMapping;
	}

	public JSObjectLiteral compileEnumLeafInfo(MEnumLeafInfo<T, C> enumLeafInfo) {
		final JsonixModule module = getModule(enumLeafInfo);
		final JSObjectLiteral mapping = this.codeModel.object();
		mapping.append(naming.type(), this.codeModel.string(naming.enumInfo()));
		mapping.append(naming.localName(), this.codeModel.string(enumLeafInfo
				.getContainerLocalName(DEFAULT_SCOPED_NAME_DELIMITER)));

		final MTypeInfo<T, C> baseTypeInfo = enumLeafInfo.getBaseTypeInfo();
		if (baseTypeInfo != null) {
			final JSAssignmentExpression baseTypeInfoDeclaration = getTypeInfoDeclaration(baseTypeInfo);
			if (!baseTypeInfoDeclaration
					.acceptExpressionVisitor(new CheckValueStringLiteralExpressionVisitor(
							"String"))) {
				mapping.append(naming.baseTypeInfo(), baseTypeInfoDeclaration);
			}
		}
		mapping.append(naming.values(), compileEnumConstrantInfos(enumLeafInfo));
		module.registerTypeInfo(mapping);
		return mapping;
	}

	public JSArrayLiteral compilePropertyInfos(MClassInfo<T, C> classInfo) {
		final JsonixModule module = getModule(classInfo);
		final JSArrayLiteral propertyInfoMappings = this.codeModel.array();
		for (MPropertyInfo<T, C> propertyInfo : classInfo.getProperties()) {
			propertyInfoMappings.append(propertyInfo
					.acceptPropertyInfoVisitor(new PropertyInfoVisitor<T, C>(
							JsonixCompiler.this, JsonixCompiler.this.naming,
							JsonixCompiler.this.codeModel,

							module)));
		}
		return propertyInfoMappings;
	}

	private JSArrayLiteral compileEnumConstrantInfos(
			MEnumLeafInfo<T, C> enumLeafInfo) {
		final JSArrayLiteral mappings = this.codeModel.array();
		for (MEnumConstantInfo<T, C> enumConstantInfo : enumLeafInfo
				.getConstants()) {
			mappings.append(this.codeModel.string(enumConstantInfo
					.getLexicalValue()));
		}
		return mappings;
	}

	public JSArrayLiteral compileElementInfos(
			Collection<MElementInfo<T, C>> elementInfos) {
		final JSArrayLiteral elementInfoMappings = this.codeModel.array();
		for (MElementInfo<T, C> elementInfo : elementInfos) {
			elementInfoMappings.append(compileElementInfo(elementInfo));
		}
		return elementInfoMappings;
	}

	public JSObjectLiteral compileElementInfo(MElementInfo<T, C> elementInfo) {
		JsonixModule module = getModule(elementInfo);
		MTypeInfo<T, C> typeInfo = elementInfo.getTypeInfo();
		MTypeInfo<T, C> scope = elementInfo.getScope();
		QName substitutionHead = elementInfo.getSubstitutionHead();

		final JSObjectLiteral value = this.codeModel.object();
		module.registerElementInfo(value);
		JSAssignmentExpression typeInfoDeclaration = getTypeInfoDeclaration(typeInfo);
		QName elementName = elementInfo.getElementName();
		value.append(naming.elementName(),
				module.createElementNameExpression(elementName));
		if (typeInfoDeclaration != null) {
			if (!typeInfoDeclaration
					.acceptExpressionVisitor(new CheckValueStringLiteralExpressionVisitor(
							"String"))) {
				value.append(naming.typeInfo(), typeInfoDeclaration);
			}
		}

		if (scope != null) {
			value.append(naming.scope(), getTypeInfoDeclaration(scope));
		}
		if (substitutionHead != null) {
			value.append(naming.substitutionHead(),
					module.createElementNameExpression(substitutionHead));
		}
		return value;
	}
}
