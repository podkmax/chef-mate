package com.chefmate.service;

import com.chefmate.model.BaseProduct;
import com.chefmate.model.Unit;
import java.math.BigDecimal;

record ImportedIngredient(String name, BigDecimal qty, Unit unit, boolean excludeForClient, BaseProduct baseProduct) { }

