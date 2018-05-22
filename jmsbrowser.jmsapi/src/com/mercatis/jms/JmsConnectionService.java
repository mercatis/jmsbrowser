package com.mercatis.jms;

import java.util.List;
import java.util.Map;


public interface JmsConnectionService {

	public JmsConnection createConnection(Map<String,String> properties) throws JmsBrowserException;

	public List<LibraryDependency> getNotIncludedLibraryNames(String os, String arch);
	
	public String getDisplayName();
}
