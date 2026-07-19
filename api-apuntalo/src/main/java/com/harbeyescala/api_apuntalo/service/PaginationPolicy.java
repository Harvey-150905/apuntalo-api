package com.harbeyescala.api_apuntalo.service;
import com.harbeyescala.api_apuntalo.exception.BadRequestException;
import com.harbeyescala.api_apuntalo.exception.ErrorCodes;
public final class PaginationPolicy {
 public static final int MAX_SIZE=100; private PaginationPolicy(){}
 public static void validate(int page,int size){if(page<0)throw new BadRequestException(ErrorCodes.INVALID_PAGE,"La página no puede ser negativa");if(size<1||size>MAX_SIZE)throw new BadRequestException(ErrorCodes.INVALID_PAGE_SIZE,"El tamaño de página debe estar entre 1 y "+MAX_SIZE);}
}
