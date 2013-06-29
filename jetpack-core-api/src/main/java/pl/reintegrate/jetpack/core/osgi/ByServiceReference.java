package pl.reintegrate.jetpack.core.osgi;

public @interface ByServiceReference {
	Class<?> interfaceName();
	String filter();
}
