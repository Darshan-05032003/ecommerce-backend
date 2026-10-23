package com.darshan.ecommerce.category;

import com.darshan.ecommerce.common.config.CacheNames;
import com.darshan.ecommerce.common.exception.ConflictException;
import com.darshan.ecommerce.common.exception.ResourceNotFoundException;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper mapper;

    public CategoryService(CategoryRepository categoryRepository, CategoryMapper mapper) {
        this.categoryRepository = categoryRepository;
        this.mapper = mapper;
    }

    // The whole list is one cache entry: it is small, read on every catalogue page and changes rarely.
    @Cacheable(cacheNames = CacheNames.CATEGORIES, key = "'all'")
    public List<CategoryResponse> list() {
        return mapper.toResponses(categoryRepository.findAll(Sort.by("name")));
    }

    public CategoryResponse getById(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true)
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new ConflictException("Category name already in use");
        }
        Category saved = categoryRepository.save(new Category(request.name(), request.description()));
        return mapper.toResponse(saved);
    }

    // Product responses embed the category name, so a rename also invalidates every cached product.
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PRODUCTS, allEntries = true)})
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = load(id);
        if (categoryRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("Category name already in use");
        }
        category.setName(request.name());
        category.setDescription(request.description());
        return mapper.toResponse(category);
    }

    // Categories are reference data, so this is a real delete; the foreign key from products
    // refuses it while any product, active or not, still points at the category.
    @Transactional
    @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true)
    public void delete(Long id) {
        Category category = load(id);
        try {
            categoryRepository.delete(category);
            categoryRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Category " + id + " still has products and cannot be deleted");
        }
    }

    private Category load(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id " + id));
    }
}
