export interface Unit {
  id: string;
  name: string;
  shortName: string;
}

export interface DishIngredient {
  id?: number;
  name: string;
  qty: number | null;
  unitId: string;
  unit?: Unit | null;
  excludeForClient?: boolean;
  baseProductId?: string | null;
}

export interface Dish {
  id?: number;
  category: string;
  title: string;
  description?: string;
  active?: boolean | null;
  ingredients: DishIngredient[];
}

export interface OrderItem {
  dishId: number;
  portions: number;
  notes?: string | null;
}

export interface Order {
  id: number;
  userId: number;
  targetDate: string;
  status?: string;
  comment?: string | null;
  items: OrderItem[];
}

export interface IngredientAggregate {
  name: string;
  totalQty: number;
  unitId?: string | null;
  unit?: Unit | null;
  stockQty?: number | null;
  requiredQty?: number | null;
  baseProductId?: string | null;
}

export interface ClientStockDto {
  id?: string;
  baseProductId: string;
  baseProductName?: string | null;
  qty: number;
  unit?: string | null;
  isFreezable?: boolean | null;
}

export interface ClientInfo {
  id: number;
  name: string | null;
  telegramId?: number | null;
}

export interface BaseProductOption {
  id: string;
  name: string;
  unit: string;
  isFreezable: boolean;
}
