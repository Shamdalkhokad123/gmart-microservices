package com.gmart.service;

import com.gmart.config.CacheConfig;
import com.gmart.entity.Category;
import com.gmart.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    @Cacheable(CacheConfig.CATEGORIES)
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Cacheable(CacheConfig.CATEGORY_BY_ID)
    public Category getCategoryById(Integer id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with ID: " + id));
    }

    @CacheEvict(cacheNames = {CacheConfig.CATEGORIES, CacheConfig.CATEGORY_BY_ID}, allEntries = true)
    public Category createCategory(Category category) {
        return categoryRepository.save(category);
    }

    @CacheEvict(cacheNames = {CacheConfig.CATEGORIES, CacheConfig.CATEGORY_BY_ID}, allEntries = true)
    public Category updateCategory(Integer id, Category updated) {
        Category existing = getCategoryById(id);
        existing.setCategoryName(updated.getCategoryName());
        existing.setCategoryDesc(updated.getCategoryDesc());
        if (updated.getImageUrl() != null && !updated.getImageUrl().isBlank()) {
            existing.setImageUrl(updated.getImageUrl());
        }
        return categoryRepository.save(existing);
    }

    @CacheEvict(cacheNames = {CacheConfig.CATEGORIES, CacheConfig.CATEGORY_BY_ID}, allEntries = true)
    public void deleteCategory(Integer id) {
        categoryRepository.deleteById(id);
    }
}
