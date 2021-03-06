/*******************************************************************************
 * Copyright (c) 2007, 2011 Intel Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Intel Corporation - Initial API and implementation
 * IBM Corporation
 *******************************************************************************/
package org.eclipse.cdt.managedbuilder.internal.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.cdt.internal.core.SafeStringInterner;
import org.eclipse.cdt.managedbuilder.core.IBuildPropertiesRestriction;
import org.eclipse.cdt.managedbuilder.core.IManagedConfigElement;

public class SupportedProperties implements IBuildPropertiesRestriction {
	public static final String SUPPORTED_PROPERTIES = "supportedProperties";				//$NON-NLS-1$
	public static final String PROPERTY = "property";				//$NON-NLS-1$
	public static final String PROPERTY_VALUE = "value";				//$NON-NLS-1$
	public static final String ID = "id";				//$NON-NLS-1$
	public static final String REQUIRED = "required";				//$NON-NLS-1$

	private HashMap<String, SupportedProperty> fSupportedProperties = new HashMap<String, SupportedProperty>();

	private class SupportedProperty {
		private boolean fIsRequired;
		private Set<String> fValues = new HashSet<String>();
		private String fId;

		SupportedProperty(String id){
			fId = id;
		}

		void updateRequired(boolean required){
			if(!fIsRequired)
				fIsRequired = required;
		}

		public String getId(){
			return fId;
		}

/*		SupportedProperty(IManagedConfigElement el) {
			fId = el.getAttribute(ID);

//			IBuildPropertyType type = mngr.getPropertyType(id);
//			if(type == null)
//				continue;

			fIsRequired = Boolean.valueOf(el.getAttribute(REQUIRED)).booleanValue();

			fValues = new HashSet();

			IManagedConfigElement values[] = el.getChildren();
			for(int k = 0; k < values.length; k++){
				IManagedConfigElement value = values[k];
				if(PROPERTY_VALUE.equals(value.getName())){
					String valueId = value.getAttribute(ID);
					if(valueId == null && valueId.length() == 0)
						continue;

//					IBuildPropertyValue val = type.getSupportedValue(valueId);
//					if(val != null)
//						set.add(val.getId());
					fValues.add(valueId);
				}
			}
		}
*/
//		public boolean isValid(){
//			return fId != null && fValues.size() != 0;
//		}

		public boolean isRequired(){
			return fIsRequired;
		}

		public void addValueIds(Set<String> ids){
			fValues.addAll(ids);
		}

		public boolean supportsValue(String id){
			return fValues.contains(id);
		}

		public String[] getSupportedValues(){
			return fValues.toArray(new String[fValues.size()]);
		}

	}

	public SupportedProperties(IManagedConfigElement el){
//		IBuildPropertyManager mngr = BuildPropertyManager.getInstance();

		IManagedConfigElement children[] = el.getChildren();
		for(int i = 0; i < children.length; i++){
			IManagedConfigElement child = children[i];
			if(PROPERTY.equals(child.getName())){
				String id = SafeStringInterner.safeIntern(child.getAttribute(ID));
				if(id == null)
					continue;

				boolean required = Boolean.valueOf(el.getAttribute(REQUIRED)).booleanValue();

//				IBuildPropertyType type = mngr.getPropertyType(id);
//				if(type == null)
//					continue;

				Set<String> set = new HashSet<String>();

				IManagedConfigElement values[] = child.getChildren();
				for(int k = 0; k < values.length; k++){
					IManagedConfigElement value = values[k];
					if(PROPERTY_VALUE.equals(value.getName())){
						String valueId = SafeStringInterner.safeIntern(value.getAttribute(ID));
						if(valueId == null || valueId.length() == 0)
							continue;

//						IBuildPropertyValue val = type.getSupportedValue(valueId);
//						if(val != null)
//							set.add(val.getId());

						set.add(valueId);
					}
				}

				if(set.size() != 0){
					SupportedProperty stored = fSupportedProperties.get(id);
					if(stored == null){
						stored = new SupportedProperty(id);
						fSupportedProperties.put(id, stored);
					}
					stored.addValueIds(set);
					stored.updateRequired(required);
				}
			}
		}

	}

//	public boolean supportsType(IBuildPropertyType type) {
//		return supportsType(type.getId());
//	}

	@Override
	public boolean supportsType(String type) {
		return fSupportedProperties.containsKey(type);
	}

	@Override
	public boolean supportsValue(String type, String value){
		boolean suports = false;
		SupportedProperty prop = fSupportedProperties.get(type);
		if(prop != null){
			suports = prop.supportsValue(value);
		}
		return suports;
	}

//	public boolean supportsValue(IBuildPropertyType type,
//			IBuildPropertyValue value) {
//		return supportsValue(type.getId(), value.getId());
//	}

	@Override
	public String[] getRequiredTypeIds() {
		List<String> list = new ArrayList<String>(fSupportedProperties.size());
		Collection<SupportedProperty> values = fSupportedProperties.values();
		for (SupportedProperty prop : values) {
			if(prop.isRequired())
				list.add(prop.getId());
		}
		return list.toArray(new String[list.size()]);
	}

	@Override
	public String[] getSupportedTypeIds() {
		String result[] = new String[fSupportedProperties.size()];
		fSupportedProperties.keySet().toArray(result);
		return result;
	}

	@Override
	public String[] getSupportedValueIds(String typeId) {
		SupportedProperty prop = fSupportedProperties.get(typeId);
		if(prop != null)
			return prop.getSupportedValues();
		return new String[0];
	}

	@Override
	public boolean requiresType(String typeId) {
		SupportedProperty prop = fSupportedProperties.get(typeId);
		if(prop != null)
			return prop.isRequired();
		return false;
	}

}
