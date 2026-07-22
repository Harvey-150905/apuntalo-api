package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.ProductRequestDto;
import com.harbeyescala.api_apuntalo.dto.ProductResponseDto;
import com.harbeyescala.api_apuntalo.dto.ProductUpdateDto;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.Product;
import com.harbeyescala.api_apuntalo.entity.Subcategory;
import com.harbeyescala.api_apuntalo.entity.enums.AuditAction;
import com.harbeyescala.api_apuntalo.entity.enums.AuditEntityType;
import com.harbeyescala.api_apuntalo.exception.DuplicateResourceException;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;
import com.harbeyescala.api_apuntalo.repository.ProductRepository;
import com.harbeyescala.api_apuntalo.repository.SubcategoryRepository;
import com.harbeyescala.api_apuntalo.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

import java.util.List;

/**
 * CRUD de productos de la Store activa. Fase 9 (F9.7): las
 * altas/modificaciones/(des)activaciones quedan auditadas. El borrado ya
 * era un soft-delete (se conserva por compatibilidad, delega en
 * {@link #setActive(Long, boolean)}); {@code PATCH /api/products/{id}/status}
 * es la vía recomendada para activar/desactivar.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final CloudinaryService cloudinaryService;
    private final FileValidationService fileValidationService;
    private final AuditEventService auditEventService;
    private final ActiveStoreContext storeContext;

    public ProductService(ProductRepository productRepository,
                        SubcategoryRepository subcategoryRepository,
                        CloudinaryService cloudinaryService,
                        FileValidationService fileValidationService,
                        AuditEventService auditEventService,
                        ActiveStoreContext storeContext) {
        this.productRepository = productRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.cloudinaryService = cloudinaryService;
        this.fileValidationService = fileValidationService;
        this.auditEventService = auditEventService;
        this.storeContext=storeContext;
    }
    @Transactional
    public ProductResponseDto save(ProductRequestDto dto, MultipartFile image) {

        Long negocioId = SecurityUtils.getNegocioId();

        // validar duplicado
        if (productRepository.existsByNameAndNegocioIdAndStoreId(dto.getName(), negocioId,storeContext.storeId())) {
            throw new DuplicateResourceException("Ya existe un producto con ese nombre en este negocio");
        }

        // obtener subcategoría
        Subcategory subcategory = subcategoryRepository
                .findByIdAndNegocioIdAndStoreId(dto.getSubcategoryId(), negocioId,storeContext.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Subcategoría no encontrada"));

        String imageUrl = null;
        String imagePublicId = null;

        // 👉 subir imagen si viene
        if (image != null && !image.isEmpty()) {
            fileValidationService.validateImage(image);

            String folder = "negocios/" + negocioId + "/products";
            Map<String, Object> result = cloudinaryService.uploadImage(image, folder);

            imageUrl = (String) result.get("secure_url");
            imagePublicId = (String) result.get("public_id");
        }

        // crear producto
        Product product = Product.builder()
                .name(dto.getName())
                .subcategory(subcategory)
                .price(MoneyPolicy.requirePositive(dto.getPrice(), "INVALID_PRODUCT_PRICE", "El precio no es válido"))
                .description(dto.getDescription())
                .imageUrl(imageUrl)
                .imagePublicId(imagePublicId)
                //.activo(dto.getActivo() != null ? dto.getActivo() : true)
                .activo(Boolean.TRUE.equals(dto.getActivo()) || dto.getActivo() == null)
                .negocio(new Negocio(negocioId))
                .store(storeContext.requireStore())
                .build();

        Product saved = productRepository.save(product);

        auditEventService.recordSuccess(AuditEntityType.PRODUCT, saved.getId(), AuditAction.PRODUCT_CREATED,
                null, Map.of("name", saved.getName(), "price", saved.getPrice(), "activo", saved.getActivo()));

        return mapToResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDto> findAllActivos() {

        Long negocioId = SecurityUtils.getNegocioId();

        return productRepository.findByNegocioIdAndStoreIdAndActivoTrue(negocioId,storeContext.storeId())
                .stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDto> findAll() {

        Long negocioId = SecurityUtils.getNegocioId();

        return productRepository.findByNegocioIdAndStoreId(negocioId,storeContext.storeId())
                .stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponseDto findById(Long id) {

        Long negocioId = SecurityUtils.getNegocioId();

        Product product = productRepository
                .findByIdAndNegocioIdAndStoreId(id, negocioId,storeContext.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        return mapToResponseDto(product);
    }

    @Transactional
    public ProductResponseDto update(Long id, ProductUpdateDto dto, MultipartFile image) {

        Long negocioId = SecurityUtils.getNegocioId();

        Product product = productRepository
                .findByIdAndNegocioIdAndStoreId(id, negocioId,storeContext.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        if (productRepository.existsByNameAndNegocioIdAndStoreIdAndIdNot(dto.getName(), negocioId,storeContext.storeId(), id)) {
            throw new DuplicateResourceException("Ya existe un producto con ese nombre en este negocio");
        }

        Subcategory subcategory = subcategoryRepository
                .findByIdAndNegocioIdAndStoreId(dto.getSubcategoryId(), negocioId,storeContext.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Subcategoría no encontrada"));

        Map<String, Object> before = Map.of("name", product.getName(), "price", product.getPrice(),
                "activo", product.getActivo());

        if (image != null && !image.isEmpty()) {
            fileValidationService.validateImage(image);

            String folder = "negocios/" + negocioId + "/products";
            var result = cloudinaryService.uploadImage(image, folder);

            String newImageUrl = (String) result.get("secure_url");
            String newImagePublicId = (String) result.get("public_id");

            if (product.getImagePublicId() != null && !product.getImagePublicId().isBlank()) {
                cloudinaryService.deleteImage(product.getImagePublicId());
            }

            product.setImageUrl(newImageUrl);
            product.setImagePublicId(newImagePublicId);
        }

        product.setName(dto.getName());
        product.setSubcategory(subcategory);
        product.setPrice(MoneyPolicy.requirePositive(dto.getPrice(), "INVALID_PRODUCT_PRICE", "El precio no es válido"));
        product.setDescription(dto.getDescription());
        product.setActivo(dto.getActivo() != null ? dto.getActivo() : product.getActivo());

        Product updated = productRepository.save(product);

        auditEventService.recordSuccess(AuditEntityType.PRODUCT, updated.getId(), AuditAction.PRODUCT_UPDATED,
                before, Map.of("name", updated.getName(), "price", updated.getPrice(), "activo", updated.getActivo()));

        return mapToResponseDto(updated);
    }

    /**
     * Activa/desactiva un producto (Fase 9, F9.7). Al desactivar, libera la
     * imagen en Cloudinary (mismo comportamiento que el antiguo borrado).
     */
    @Transactional
    public ProductResponseDto setActive(Long id, boolean active) {
        Long negocioId = SecurityUtils.getNegocioId();

        Product product = productRepository
                .findByIdAndNegocioIdAndStoreId(id, negocioId, storeContext.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        boolean previous = Boolean.TRUE.equals(product.getActivo());
        if (previous == active) {
            return mapToResponseDto(product);
        }

        if (!active && product.getImagePublicId() != null && !product.getImagePublicId().isBlank()) {
            cloudinaryService.deleteImage(product.getImagePublicId());
            product.setImageUrl(null);
            product.setImagePublicId(null);
        }

        product.setActivo(active);
        Product saved = productRepository.save(product);

        auditEventService.recordSuccess(AuditEntityType.PRODUCT, id,
                active ? AuditAction.PRODUCT_ACTIVATED : AuditAction.PRODUCT_DEACTIVATED,
                Map.of("activo", previous), Map.of("activo", active));

        return mapToResponseDto(saved);
    }

    /**
     * @deprecated Fase 9 (F9.7): usa {@link #setActive(Long, boolean)} vía
     * {@code PATCH /api/products/{id}/status}. Se conserva por
     * compatibilidad con el endpoint {@code DELETE} existente (soft-delete).
     */
    @Deprecated
    @Transactional
    public void delete(Long id) {
        setActive(id, false);
    }

    private ProductResponseDto mapToResponseDto(Product product) {
        return ProductResponseDto.builder()
                .id(product.getId())
                .name(product.getName())
                .category(product.getSubcategory().getCategory())
                .subcategoryId(product.getSubcategory().getId())
                .subcategoryNombre(product.getSubcategory().getNombre())
                .price(product.getPrice())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .imagePublicId(product.getImagePublicId())
                .activo(product.getActivo())
                .negocioId(product.getNegocio().getId())
                .negocioNombre(product.getNegocio().getNombre())
                .build();
    }
}
