package com.dookie.notification.adapter;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registry for vendor adapters.
 * Routes notifications to the appropriate adapter based on vendor name.
 */
@Component
public class VendorAdapterRegistry {
    
    private static final String DEFAULT_ADAPTER_NAME = "generic";
    
    private final Map<String, VendorAdapter> adapters;
    private final VendorAdapter defaultAdapter;
    
    /**
     * Creates a new VendorAdapterRegistry with the provided adapters.
     * 
     * @param adapterList list of all available vendor adapters
     */
    public VendorAdapterRegistry(List<VendorAdapter> adapterList) {
        this.adapters = adapterList.stream()
            .collect(Collectors.toMap(VendorAdapter::getVendorName, adapter -> adapter));
        this.defaultAdapter = adapters.get(DEFAULT_ADAPTER_NAME);
        
        if (this.defaultAdapter == null && !adapters.isEmpty()) {
            throw new IllegalStateException(
                "No default adapter found with name '" + DEFAULT_ADAPTER_NAME + "'. " +
                "Please ensure a GenericHttpAdapter is registered."
            );
        }
    }
    
    /**
     * Gets the adapter for the specified vendor.
     * Falls back to the default adapter if no specific adapter is found.
     * 
     * @param vendorName the vendor name
     * @return the appropriate vendor adapter
     */
    public VendorAdapter getAdapter(String vendorName) {
        VendorAdapter adapter = adapters.get(vendorName);
        if (adapter != null) {
            return adapter;
        }
        return defaultAdapter;
    }
}
