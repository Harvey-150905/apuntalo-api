package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.entity.Store;
import com.harbeyescala.api_apuntalo.exception.StoreNotFoundException;
import com.harbeyescala.api_apuntalo.repository.StoreRepository;
import com.harbeyescala.api_apuntalo.security.CurrentUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ActiveStoreContext {
    private final CurrentUser currentUser;
    private final StoreRepository stores;
    public ActiveStoreContext(CurrentUser currentUser, StoreRepository stores) {
        this.currentUser=currentUser; this.stores=stores;
    }
    public Long tenantId(){ return currentUser.getTenantId(); }
    public Long storeId(){ return currentUser.requireCurrentStoreId(); }
    @Transactional(readOnly=true)
    public Store requireStore(){
        return stores.findByIdAndNegocioId(storeId(),tenantId()).orElseThrow(StoreNotFoundException::new);
    }
}
