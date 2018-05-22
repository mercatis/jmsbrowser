package com.mercatis.jms;


public class LibraryDependency {
	public String name;
	public String subpath;
	
	public LibraryDependency(String name) {
		this.name = name;
	}

	public LibraryDependency(String name, String subpath) {
		this.name = name;
		this.subpath = subpath;

	}
	
	@Override
	public String toString() {
		return subpath==null ? name : name+" (from "+subpath+")";
	}

	public String getPath() {
		return subpath==null ? name : subpath+'/'+name;
	}
}
