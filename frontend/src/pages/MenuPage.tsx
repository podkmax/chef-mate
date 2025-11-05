import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import EditIcon from "@mui/icons-material/Edit";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import {
  Alert,
  AlertColor,
  Box,
  Button,
  Checkbox,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  IconButton,
  InputLabel,
  LinearProgress,
  MenuItem,
  Paper,
  Select,
  Snackbar,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TableSortLabel,
  TextField,
  Typography
} from "@mui/material";
import { ChangeEvent, useCallback, useEffect, useMemo, useState } from "react";
import { apiClient } from "../api/client";
import { Dish, Unit } from "../types";
import { SelectChangeEvent } from "@mui/material/Select";

interface IngredientForm {
  id?: number;
  name: string;
  qty: string;
  unitId: string;
  excludeForClient: boolean;
}

interface DishFormState {
  id?: number;
  category: string;
  title: string;
  description: string;
  ingredients: IngredientForm[];
}

const emptyForm: DishFormState = {
  category: "",
  title: "",
  description: "",
  ingredients: []
};

type SortField = "title" | "category";
type SortDirection = "asc" | "desc";

export function MenuPage() {
  const [dishes, setDishes] = useState<Dish[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [formState, setFormState] = useState<DishFormState>(emptyForm);
  const [saving, setSaving] = useState(false);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: AlertColor }>({
    open: false,
    message: "",
    severity: "success"
  });
  const [searchTerm, setSearchTerm] = useState("");
  const [sortField, setSortField] = useState<SortField>("title");
  const [sortDirection, setSortDirection] = useState<SortDirection>("asc");
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [units, setUnits] = useState<Unit[]>([]);

  const isEdit = Boolean(formState.id);

  const showSnackbar = useCallback((message: string, severity: AlertColor) => {
    setSnackbar({ open: true, message, severity });
  }, []);

  const loadUnits = useCallback(async () => {
    try {
      const { data } = await apiClient.get<Unit[]>("/units");
      setUnits(data);
    } catch (err) {
      console.error("Failed to load units", err);
      showSnackbar("Не удалось загрузить список единиц измерения.", "error");
    }
  }, [showSnackbar]);

  const loadDishes = useCallback(async () => {
    try {
      setLoading(true);
      const { data } = await apiClient.get<Dish[]>("/menu");
      setDishes(data);
      setError(null);
    } catch (err) {
      setError("Не удалось загрузить меню.");
      showSnackbar("Не удалось загрузить меню.", "error");
    } finally {
      setLoading(false);
    }
  }, [showSnackbar]);

useEffect(() => {
  loadUnits();
  loadDishes();
}, [loadUnits, loadDishes]);

useEffect(() => {
  setPage(0);
}, [searchTerm]);

  useEffect(() => {
    if (!units.length) {
      return;
    }
    setFormState((prev) => {
      if (!prev.ingredients.some((ing) => !ing.unitId || ing.unitId.length === 0)) {
        return prev;
      }
      return {
        ...prev,
        ingredients: prev.ingredients.map((ing) =>
          ing.unitId && ing.unitId.length > 0 ? ing : { ...ing, unitId: units[0].id }
        )
      };
    });
  }, [units]);

  const filteredDishes = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    const filtered = term
      ? dishes.filter((dish) => {
          const haystack = `${dish.title ?? ""} ${dish.category ?? ""}`.toLowerCase();
          return haystack.includes(term);
        })
      : dishes.slice();
    filtered.sort((a, b) => {
      const dir = sortDirection === "asc" ? 1 : -1;
      if (sortField === "title") {
        return ((a.title ?? "").localeCompare(b.title ?? "")) * dir;
      }
      return ((a.category ?? "").localeCompare(b.category ?? "")) * dir;
    });
    return filtered;
  }, [dishes, searchTerm, sortField, sortDirection]);

  const paginatedDishes = useMemo(() => {
    const start = page * rowsPerPage;
    return filteredDishes.slice(start, start + rowsPerPage);
  }, [filteredDishes, page, rowsPerPage]);

  const openCreateDialog = () => {
    setFormState({
      ...emptyForm,
      ingredients: [
        {
          name: "",
          qty: "",
          unitId: units[0]?.id ?? "",
          excludeForClient: false
        }
      ]
    });
    setDialogOpen(true);
  };

  const openEditDialog = (dish: Dish) => {
    setFormState({
      id: dish.id,
      category: dish.category ?? "",
      title: dish.title ?? "",
      description: dish.description ?? "",
      ingredients: (dish.ingredients ?? []).map((ing) => ({
        id: ing.id,
        name: ing.name ?? "",
        qty: ing.qty != null ? String(ing.qty) : "",
        unitId: ing.unitId ?? ing.unit?.id ?? "",
        excludeForClient: Boolean(ing.excludeForClient)
      }))
    });
    setDialogOpen(true);
  };

  const closeDialog = () => {
    setDialogOpen(false);
    setSaving(false);
  };

  const updateIngredient = (index: number, patch: Partial<IngredientForm>) => {
    setFormState((prev) => ({
      ...prev,
      ingredients: prev.ingredients.map((ing, idx) => (idx === index ? { ...ing, ...patch } : ing))
    }));
  };

  const addIngredient = () => {
    setFormState((prev) => ({
      ...prev,
      ingredients: [
        ...prev.ingredients,
        { name: "", qty: "", unitId: units[0]?.id ?? "", excludeForClient: false }
      ]
    }));
  };

  const removeIngredient = (index: number) => {
    setFormState((prev) => ({
      ...prev,
      ingredients: prev.ingredients.filter((_, idx) => idx !== index)
    }));
  };

  const handleImport = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    const formData = new FormData();
    formData.append("file", file);
    try {
      const { data } = await apiClient.post<{ created: number; updated: number; skipped: number }>(
        "/menu/import",
        formData,
        { headers: { "Content-Type": "multipart/form-data" } }
      );
      showSnackbar(`Импорт завершён. Создано: ${data.created}, обновлено: ${data.updated}, пропущено: ${data.skipped}.`, "success");
      await loadDishes();
    } catch (err) {
      showSnackbar("Ошибка импорта меню.", "error");
    } finally {
      event.target.value = "";
    }
  };

  const handleSubmit = async () => {
    if (!formState.title.trim() || !formState.category.trim()) {
      showSnackbar("Категория и название обязательны.", "warning");
      return;
    }
    if (!formState.ingredients.length) {
      showSnackbar("Добавьте хотя бы один ингредиент.", "warning");
      return;
    }
    if (formState.ingredients.some((ing) => !ing.name.trim() || !ing.unitId)) {
      showSnackbar("Для каждого ингредиента укажите название и единицу измерения.", "warning");
      return;
    }
    setSaving(true);
    const payload: Dish = {
      id: formState.id,
      category: formState.category.trim(),
      title: formState.title.trim(),
      description: formState.description.trim() || undefined,
      active: true,
      ingredients: formState.ingredients
        .filter((ing) => ing.name.trim())
        .map((ing) => ({
          id: ing.id,
          name: ing.name.trim(),
          qty: ing.qty ? Number(ing.qty) : 0,
          unitId: ing.unitId,
          excludeForClient: ing.excludeForClient
        }))
    };
    try {
      if (payload.id) {
        await apiClient.put(`/menu/${payload.id}`, payload);
      } else {
        await apiClient.post("/menu", payload);
      }
      showSnackbar("Блюдо сохранено.", "success");
      closeDialog();
      await loadDishes();
    } catch (err) {
      showSnackbar("Не удалось сохранить блюдо.", "error");
      setSaving(false);
    }
  };

  const handleDelete = async (dishId?: number, title?: string) => {
    if (!dishId) return;
    if (!window.confirm(`Удалить блюдо "${title ?? dishId}"?`)) return;
    try {
      await apiClient.delete(`/menu/${dishId}`);
      showSnackbar("Блюдо удалено.", "success");
      await loadDishes();
    } catch (err) {
      showSnackbar("Не удалось удалить блюдо.", "error");
    }
  };

  const handleSearchChange = (event: ChangeEvent<HTMLInputElement>) => {
    setSearchTerm(event.target.value);
  };

  const handleSort = (field: SortField) => {
    setSortField((prevField) => {
      if (prevField === field) {
        setSortDirection((prevDirection) => (prevDirection === "asc" ? "desc" : "asc"));
        return prevField;
      }
      setSortDirection("asc");
      return field;
    });
  };

  const handlePageChange = (_: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleRowsPerPageChange = (event: ChangeEvent<HTMLInputElement>) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const handleSnackbarClose = () => setSnackbar((prev) => ({ ...prev, open: false }));

  return (
    <Stack spacing={3}>
      <Box display="flex" alignItems="center" justifyContent="space-between" flexWrap="wrap" gap={2}>
        <Typography variant="h4">Меню</Typography>
        <Stack direction="row" spacing={2}>
          <TextField
            value={searchTerm}
            onChange={handleSearchChange}
            size="small"
            placeholder="Поиск по названию или категории"
          />
          <Button variant="outlined" component="label" startIcon={<UploadFileIcon />}>
            Импорт Excel
            <input hidden accept=".xlsx" type="file" onChange={handleImport} />
          </Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={openCreateDialog}>
            Новое блюдо
          </Button>
        </Stack>
      </Box>

      {error && <Alert severity="error">{error}</Alert>}

      <Paper>
        {loading && <LinearProgress />}
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell sortDirection={sortField === "title" ? sortDirection : false}>
                  <TableSortLabel
                    active={sortField === "title"}
                    direction={sortField === "title" ? sortDirection : "asc"}
                    onClick={() => handleSort("title")}
                  >
                    Название
                  </TableSortLabel>
                </TableCell>
                <TableCell sortDirection={sortField === "category" ? sortDirection : false}>
                  <TableSortLabel
                    active={sortField === "category"}
                    direction={sortField === "category" ? sortDirection : "asc"}
                    onClick={() => handleSort("category")}
                  >
                    Категория
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">Действия</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={3} align="center">
                    <CircularProgress size={24} />
                  </TableCell>
                </TableRow>
              ) : paginatedDishes.length ? (
                paginatedDishes.map((dish) => (
                  <TableRow key={dish.id ?? dish.title}>
                    <TableCell>{dish.title}</TableCell>
                    <TableCell>{dish.category}</TableCell>
                    <TableCell align="right">
                      <IconButton size="small" onClick={() => openEditDialog(dish)} aria-label="edit">
                        <EditIcon />
                      </IconButton>
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => handleDelete(dish.id, dish.title ?? undefined)}
                        aria-label="delete"
                      >
                        <DeleteIcon />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={3} align="center">
                    Ничего не найдено.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div"
          count={filteredDishes.length}
          page={page}
          onPageChange={handlePageChange}
          rowsPerPage={rowsPerPage}
          onRowsPerPageChange={handleRowsPerPageChange}
          rowsPerPageOptions={[10, 25, 50]}
        />
      </Paper>

      <Dialog open={dialogOpen} onClose={closeDialog} maxWidth="md" fullWidth>
        <DialogTitle>{isEdit ? "Редактирование блюда" : "Новое блюдо"}</DialogTitle>
        <DialogContent sx={{ display: "flex", flexDirection: "column", gap: 2, mt: 1 }}>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField
              label="Категория"
              fullWidth
              value={formState.category}
              onChange={(e) => setFormState((prev) => ({ ...prev, category: e.target.value }))}
            />
            <TextField
              label="Название"
              fullWidth
              value={formState.title}
              onChange={(e) => setFormState((prev) => ({ ...prev, title: e.target.value }))}
            />
          </Stack>
          <TextField
            label="Описание"
            multiline
            minRows={2}
            value={formState.description}
            onChange={(e) => setFormState((prev) => ({ ...prev, description: e.target.value }))}
          />
          <Box>
            <Typography variant="subtitle1" sx={{ mb: 1 }}>
              Ингредиенты
            </Typography>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Название</TableCell>
                  <TableCell>Кол-во</TableCell>
                  <TableCell>Ед.</TableCell>
                  <TableCell>Скрыть для клиента</TableCell>
                  <TableCell align="right">
                    <IconButton size="small" onClick={addIngredient} aria-label="add-ingredient">
                      <AddIcon fontSize="inherit" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {formState.ingredients.map((ing, idx) => (
                  <TableRow key={idx}>
                    <TableCell>
                      <TextField
                        size="small"
                        fullWidth
                        value={ing.name}
                        onChange={(e) => updateIngredient(idx, { name: e.target.value })}
                      />
                    </TableCell>
                    <TableCell>
                      <TextField
                        size="small"
                        type="number"
                        inputProps={{ min: 0, step: "0.01" }}
                        value={ing.qty}
                        onChange={(e) => updateIngredient(idx, { qty: e.target.value })}
                      />
                    </TableCell>
                    <TableCell>
                      <FormControl size="small" fullWidth disabled={!units.length}>
                        <InputLabel id={`unit-select-${idx}`}>Ед.</InputLabel>
                        <Select
                          labelId={`unit-select-${idx}`}
                          label="Ед."
                          value={ing.unitId}
                          displayEmpty
                          onChange={(event: SelectChangeEvent<string>) =>
                            updateIngredient(idx, { unitId: event.target.value })
                          }
                        >
                          {units.length ? (
                            units.map((unit) => (
                              <MenuItem key={unit.id} value={unit.id}>
                                {unit.shortName} — {unit.name}
                              </MenuItem>
                            ))
                          ) : (
                            <MenuItem value="" disabled>
                              Нет данных
                            </MenuItem>
                          )}
                        </Select>
                      </FormControl>
                    </TableCell>
                    <TableCell align="center">
                      <Checkbox
                        checked={ing.excludeForClient}
                        onChange={(e) => updateIngredient(idx, { excludeForClient: e.target.checked })}
                      />
                    </TableCell>
                    <TableCell align="right">
                      <IconButton size="small" onClick={() => removeIngredient(idx)} aria-label="remove-ingredient">
                        <DeleteIcon fontSize="inherit" />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))}
                {!formState.ingredients.length && (
                  <TableRow>
                    <TableCell colSpan={5} align="center">
                      Добавьте ингредиенты.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeDialog}>Отмена</Button>
          <Button onClick={handleSubmit} variant="contained" disabled={saving}>
            Сохранить
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={5000}
        onClose={handleSnackbarClose}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert onClose={handleSnackbarClose} severity={snackbar.severity} sx={{ width: "100%" }}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Stack>
  );
}
