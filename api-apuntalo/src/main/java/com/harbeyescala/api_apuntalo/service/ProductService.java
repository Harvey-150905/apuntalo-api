package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.ProductRequestDto;
import com.harbeyescala.api_apuntalo.dto.ProductResponseDto;
import com.harbeyescala.api_apuntalo.dto.ProductUpdateDto;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.Product;
import com.harbeyescala.api_apuntalo.entity.Subcategory;
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

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final CloudinaryService cloudinaryService;
    private final FileValidationService fileValidationService;

    public ProductService(ProductRepository productRepository,
                        SubcategoryRepository subcategoryRepository,
                        CloudinaryService cloudinaryService,
                        FileValidationService fileValidationService) {
        this.productRepository = productRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.cloudinaryService = cloudinaryService;
        this.fileValidationService = fileValidationService;
    }
    public ProductResponseDto save(ProductRequestDto dto, MultipartFile image) {

        Long negocioId = SecurityUtils.getNegocioId();

        // validar duplicado
        if (productRepository.existsByNameAndNegocioId(dto.getName(), negocioId)) {
            throw new DuplicateResourceException("Ya existe un producto con ese nombre en este negocio");
        }

        // obtener subcategoría
        Subcategory subcategory = subcategoryRepository
                .findByIdAndNegocioId(dto.getSubcategoryId(), negocioId)
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
                .price(dto.getPrice())
                .description(dto.getDescription())
                .imageUrl(imageUrl)
                .imagePublicId(imagePublicId)
                //.activo(dto.getActivo() != null ? dto.getActivo() : true)
                .activo(Boolean.TRUE.equals(dto.getActivo()) || dto.getActivo() == null)
                .negocio(new Negocio(negocioId))
                .build();

        Product saved = productRepository.save(product);

        return mapToResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDto> findAllActivos() {

        Long negocioId = SecurityUtils.getNegocioId();

        return productRepository.findByNegocioIdAndActivoTrue(negocioId)
                .stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDto> findAll() {

        Long negocioId = SecurityUtils.getNegocioId();

        return productRepository.findByNegocioId(negocioId)
                .stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponseDto findById(Long id) {

        Long negocioId = SecurityUtils.getNegocioId();

        Product product = productRepository
                .findByIdAndNegocioId(id, negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        return mapToResponseDto(product);
    }

    public ProductResponseDto update(Long id, ProductUpdateDto dto, MultipartFile image) {

        Long negocioId = SecurityUtils.getNegocioId();

        Product product = productRepository
                .findByIdAndNegocioId(id, negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        if (productRepository.existsByNameAndNegocioIdAndIdNot(dto.getName(), negocioId, id)) {
            throw new DuplicateResourceException("Ya existe un producto con ese nombre en este negocio");
        }

        Subcategory subcategory = subcategoryRepository
                .findByIdAndNegocioId(dto.getSubcategoryId(), negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Subcategoría no encontrada"));

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
        product.setPrice(dto.getPrice());
        product.setDescription(dto.getDescription());
        product.setActivo(dto.getActivo() != null ? dto.getActivo() : product.getActivo());

        Product updated = productRepository.save(product);

        return mapToResponseDto(updated);
    }
    public void delete(Long id) {

        Long negocioId = SecurityUtils.getNegocioId();

        Product product = productRepository
                .findByIdAndNegocioId(id, negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        if (product.getImagePublicId() != null && !product.getImagePublicId().isBlank()) {
            cloudinaryService.deleteImage(product.getImagePublicId());
            product.setImageUrl(null);
            product.setImagePublicId(null);
        }

        product.setActivo(false);

        productRepository.save(product);
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
