import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import EditIcon from "@mui/icons-material/Edit";
import PeopleIcon from "@mui/icons-material/PeopleAlt";
import {
  Alert,
  AlertColor,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  LinearProgress,
  MenuItem,
  Paper,
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
import { ChangeEvent, useEffect, useMemo, useState } from "react";
import { apiClient } from "../api/client";
import { BaseProductOption, ClientInfo, ClientStockDto } from "../types";

type ClientSortField = "name" | "telegramId";
type StockSortField = "name" | "qty";
type SortDirection = "asc" | "desc";

type DialogMode = "add" | "edit";

interface StockForm {
  baseProductId: string;
  qty: string;
}

export function ClientStocksPage() {
  const [clients, setClients] = useState<ClientInfo[]>([]);
  const [loadingClients, setLoadingClients] = useState(true);
  const [clientsError, setClientsError] = useState<string | null>(null);

  const [baseProducts, setBaseProducts] = useState<BaseProductOption[]>([]);

  const [selectedClient, setSelectedClient] = useState<ClientInfo | null>(null);
  const [stock, setStock] = useState<ClientStockDto[]>([]);
  const [loadingStock, setLoadingStock] = useState(false);

  const [clientSearch, setClientSearch] = useState("");
  const [clientSortField, setClientSortField] = useState<ClientSortField>("name");
  const [clientSortDirection, setClientSortDirection] = useState<SortDirection>("asc");
  const [clientPage, setClientPage] = useState(0);
  const [clientRowsPerPage, setClientRowsPerPage] = useState(10);

  const [stockSearch, setStockSearch] = useState("");
  const [stockSortField, setStockSortField] = useState<StockSortField>("name");
  const [stockSortDirection, setStockSortDirection] = useState<SortDirection>("asc");
  const [stockPage, setStockPage] = useState(0);
  const [stockRowsPerPage, setStockRowsPerPage] = useState(10);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogMode, setDialogMode] = useState<DialogMode>("add");
  const [editingStock, setEditingStock] = useState<ClientStockDto | null>(null);
  const [form, setForm] = useState<StockForm>({ baseProductId: "", qty: "0" });
  const [saving, setSaving] = useState(false);

  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: AlertColor } | null>(null);

  useEffect(() => {
    const loadClients = async () => {
      try {
        setLoadingClients(true);
        const { data } = await apiClient.get<ClientInfo[]>("/client");
        setClients(data);
        setClientsError(null);
      } catch (err) {
        setClientsError("Не удалось загрузить список клиентов.");
      } finally {
        setLoadingClients(false);
      }
    };
    loadClients();
  }, []);

  useEffect(() => {
    const loadProducts = async () => {
      try {
        const { data } = await apiClient.get<BaseProductOption[]>("/base-product");
        setBaseProducts(data.filter((bp) => bp.isFreezable));
      } catch (err) {
        setSnackbar({ open: true, message: "Не удалось загрузить базовые продукты.", severity: "error" });
      }
    };
    loadProducts();
  }, []);

  useEffect(() => {
    setClientPage(0);
  }, [clientSearch]);

  useEffect(() => {
    setStockPage(0);
  }, [stockSearch, selectedClient]);

  const showSnackbar = (message: string, severity: AlertColor) => {
    setSnackbar({ open: true, message, severity });
  };

  const handleSnackbarClose = () => setSnackbar(null);

  const filteredClients = useMemo(() => {
    const term = clientSearch.trim().toLowerCase();
    const filtered = term
      ? clients.filter((client) => {
          const haystack = `${client.name ?? ""} ${client.telegramId ?? ""}`.toLowerCase();
          return haystack.includes(term);
        })
      : clients.slice();
    filtered.sort((a, b) => {
      const dir = clientSortDirection === "asc" ? 1 : -1;
      if (clientSortField === "name") {
        return ((a.name ?? "").localeCompare(b.name ?? "")) * dir;
      }
      const left = a.telegramId ?? 0;
      const right = b.telegramId ?? 0;
      return (left - right) * dir;
    });
    return filtered;
  }, [clients, clientSearch, clientSortField, clientSortDirection]);

  const paginatedClients = useMemo(() => {
    const start = clientPage * clientRowsPerPage;
    return filteredClients.slice(start, start + clientRowsPerPage);
  }, [filteredClients, clientPage, clientRowsPerPage]);

  const filteredStock = useMemo(() => {
    const term = stockSearch.trim().toLowerCase();
    const filtered = term
      ? stock.filter((entry) => {
          const haystack = `${entry.baseProductName ?? ""} ${entry.qty}`.toLowerCase();
          return haystack.includes(term);
        })
      : stock.slice();
    filtered.sort((a, b) => {
      const dir = stockSortDirection === "asc" ? 1 : -1;
      if (stockSortField === "name") {
        return ((a.baseProductName ?? "").localeCompare(b.baseProductName ?? "")) * dir;
      }
      return (Number(a.qty) - Number(b.qty)) * dir;
    });
    return filtered;
  }, [stock, stockSearch, stockSortField, stockSortDirection]);

  const paginatedStock = useMemo(() => {
    const start = stockPage * stockRowsPerPage;
    return filteredStock.slice(start, start + stockRowsPerPage);
  }, [filteredStock, stockPage, stockRowsPerPage]);

  const loadStock = async (clientId: number) => {
    try {
      setLoadingStock(true);
      const { data } = await apiClient.get<ClientStockDto[]>(`/client/${clientId}/stock`);
      setStock(data.map((item) => ({ ...item, qty: typeof item.qty === "number" ? item.qty : Number(item.qty) })));
    } catch (err) {
      showSnackbar("Не удалось загрузить склад клиента.", "error");
    } finally {
      setLoadingStock(false);
    }
  };

  const handleManage = async (client: ClientInfo) => {
    setSelectedClient(client);
    setStockPage(0);
    setStockSearch("");
    await loadStock(client.id);
  };

  const handleAdd = () => {
    if (!baseProducts.length) {
      showSnackbar("Сначала добавьте базовые продукты (is_freezable = true).", "warning");
      return;
    }
    const first = baseProducts[0];
    setDialogMode("add");
    setEditingStock(null);
    setForm({ baseProductId: first.id, qty: "0" });
    setDialogOpen(true);
  };

  const handleEdit = (entry: ClientStockDto) => {
    setDialogMode("edit");
    setEditingStock(entry);
    setForm({ baseProductId: entry.baseProductId, qty: entry.qty.toString() });
    setDialogOpen(true);
  };

  const handleDelete = async (entry: ClientStockDto) => {
    if (!selectedClient || !entry.id) return;
    if (!window.confirm("Удалить позицию склада?")) return;
    try {
      await apiClient.delete(`/client/${selectedClient.id}/stock/${entry.id}`);
      showSnackbar("Позиция удалена.", "success");
      await loadStock(selectedClient.id);
    } catch (err) {
      showSnackbar("Не удалось удалить позицию.", "error");
    }
  };

  const handleDialogClose = () => {
    setDialogOpen(false);
    setEditingStock(null);
    setForm({ baseProductId: baseProducts[0]?.id ?? "", qty: "0" });
  };

  const handleDialogSave = async () => {
    if (!selectedClient) return;
    const qtyNumber = Number(form.qty);
    if (Number.isNaN(qtyNumber) || qtyNumber < 0) {
      showSnackbar("Количество должно быть неотрицательным.", "warning");
      return;
    }
    if (!form.baseProductId) {
      showSnackbar("Выберите базовый продукт.", "warning");
      return;
    }
    try {
      setSaving(true);
      await apiClient.post(`/client/${selectedClient.id}/stock`, {
        id: dialogMode === "edit" ? editingStock?.id : undefined,
        baseProductId: form.baseProductId,
        qty: qtyNumber
      });
      showSnackbar("Склад обновлён.", "success");
      await loadStock(selectedClient.id);
      handleDialogClose();
    } catch (err) {
      showSnackbar("Не удалось сохранить склад.", "error");
    } finally {
      setSaving(false);
    }
  };

  const handleClientSort = (field: ClientSortField) => {
    setClientSortField((prevField) => {
      if (prevField === field) {
        setClientSortDirection((prevDir) => (prevDir === "asc" ? "desc" : "asc"));
        return prevField;
      }
      setClientSortDirection("asc");
      return field;
    });
  };

  const handleStockSort = (field: StockSortField) => {
    setStockSortField((prevField) => {
      if (prevField === field) {
        setStockSortDirection((prevDir) => (prevDir === "asc" ? "desc" : "asc"));
        return prevField;
      }
      setStockSortDirection("asc");
      return field;
    });
  };

  const handleClientPageChange = (_: unknown, newPage: number) => setClientPage(newPage);
  const handleClientRowsChange = (event: ChangeEvent<HTMLInputElement>) => {
    setClientRowsPerPage(parseInt(event.target.value, 10));
    setClientPage(0);
  };

  const handleStockPageChange = (_: unknown, newPage: number) => setStockPage(newPage);
  const handleStockRowsChange = (event: ChangeEvent<HTMLInputElement>) => {
    setStockRowsPerPage(parseInt(event.target.value, 10));
    setStockPage(0);
  };

  const selectedProduct = baseProducts.find((bp) => bp.id === form.baseProductId) ?? null;

  return (
    <Stack spacing={3}>
      <Typography variant="h4" display="flex" alignItems="center" gap={1}>
        <PeopleIcon /> Клиентские склады
      </Typography>

      {clientsError && <Alert severity="error">{clientsError}</Alert>}

      <Paper>
        {loadingClients && <LinearProgress />}
        <Box px={2} py={1} display="flex" justifyContent="flex-end">
          <TextField
            size="small"
            value={clientSearch}
            onChange={(e) => setClientSearch(e.target.value)}
            placeholder="Поиск по имени или Telegram ID"
          />
        </Box>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell sortDirection={clientSortField === "name" ? clientSortDirection : false}>
                  <TableSortLabel
                    active={clientSortField === "name"}
                    direction={clientSortField === "name" ? clientSortDirection : "asc"}
                    onClick={() => handleClientSort("name")}
                  >
                    Имя
                  </TableSortLabel>
                </TableCell>
                <TableCell sortDirection={clientSortField === "telegramId" ? clientSortDirection : false}>
                  <TableSortLabel
                    active={clientSortField === "telegramId"}
                    direction={clientSortField === "telegramId" ? clientSortDirection : "asc"}
                    onClick={() => handleClientSort("telegramId")}
                  >
                    Telegram ID
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">Действия</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loadingClients ? (
                <TableRow>
                  <TableCell colSpan={3} align="center">
                    <CircularProgress size={24} />
                  </TableCell>
                </TableRow>
              ) : paginatedClients.length ? (
                paginatedClients.map((client) => (
                  <TableRow key={client.id} selected={selectedClient?.id === client.id}>
                    <TableCell>{client.name ?? `Клиент #${client.id}`}</TableCell>
                    <TableCell>{client.telegramId ?? "—"}</TableCell>
                    <TableCell align="right">
                      <Button variant="outlined" onClick={() => handleManage(client)}>
                        Управлять складом
                      </Button>
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={3} align="center">
                    Клиенты не найдены.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div"
          count={filteredClients.length}
          page={clientPage}
          onPageChange={handleClientPageChange}
          rowsPerPage={clientRowsPerPage}
          onRowsPerPageChange={handleClientRowsChange}
          rowsPerPageOptions={[10, 25, 50]}
        />
      </Paper>

      {selectedClient && (
        <Stack spacing={2}>
          <Box display="flex" alignItems="center" justifyContent="space-between" flexWrap="wrap" gap={2}>
            <Typography variant="h5">Склад клиента {selectedClient.name ?? `#${selectedClient.id}`}</Typography>
            <Stack direction="row" spacing={2}>
              <TextField
                size="small"
                value={stockSearch}
                onChange={(e) => setStockSearch(e.target.value)}
                placeholder="Фильтр по названию"
              />
              <Button variant="contained" startIcon={<AddIcon />} onClick={handleAdd} disabled={!baseProducts.length}>
                Добавить позицию
              </Button>
            </Stack>
          </Box>
          <Paper>
            {loadingStock && <LinearProgress />}
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell sortDirection={stockSortField === "name" ? stockSortDirection : false}>
                      <TableSortLabel
                        active={stockSortField === "name"}
                        direction={stockSortField === "name" ? stockSortDirection : "asc"}
                        onClick={() => handleStockSort("name")}
                      >
                        Базовый продукт
                      </TableSortLabel>
                    </TableCell>
                    <TableCell sortDirection={stockSortField === "qty" ? stockSortDirection : false}>
                      <TableSortLabel
                        active={stockSortField === "qty"}
                        direction={stockSortField === "qty" ? stockSortDirection : "asc"}
                        onClick={() => handleStockSort("qty")}
                      >
                        Количество
                      </TableSortLabel>
                    </TableCell>
                    <TableCell>Единица</TableCell>
                    <TableCell align="right">Действия</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {loadingStock ? (
                    <TableRow>
                      <TableCell colSpan={4} align="center">
                        <CircularProgress size={24} />
                      </TableCell>
                    </TableRow>
                  ) : paginatedStock.length ? (
                    paginatedStock.map((entry) => (
                      <TableRow key={entry.id ?? entry.baseProductId}>
                        <TableCell>{entry.baseProductName ?? entry.baseProductId}</TableCell>
                        <TableCell>{entry.qty}</TableCell>
                        <TableCell>{entry.unit ?? ""}</TableCell>
                        <TableCell align="right">
                          <IconButton color="primary" onClick={() => handleEdit(entry)} aria-label="edit">
                            <EditIcon />
                          </IconButton>
                          <IconButton color="error" onClick={() => handleDelete(entry)} aria-label="delete">
                            <DeleteIcon />
                          </IconButton>
                        </TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={4} align="center">
                        Склад пуст.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              component="div"
              count={filteredStock.length}
              page={stockPage}
              onPageChange={handleStockPageChange}
              rowsPerPage={stockRowsPerPage}
              onRowsPerPageChange={handleStockRowsChange}
              rowsPerPageOptions={[10, 25, 50]}
            />
          </Paper>
        </Stack>
      )}

      <Dialog open={dialogOpen} onClose={handleDialogClose} fullWidth maxWidth="sm">
        <DialogTitle>{dialogMode === "add" ? "Добавить позицию" : "Изменить количество"}</DialogTitle>
        <DialogContent sx={{ display: "flex", flexDirection: "column", gap: 2, mt: 1 }}>
          <TextField
            select
            label="Базовый продукт"
            value={form.baseProductId}
            onChange={(e) => setForm((prev) => ({ ...prev, baseProductId: e.target.value }))}
            disabled={dialogMode === "edit"}
          >
            {baseProducts.map((bp) => (
              <MenuItem key={bp.id} value={bp.id}>
                {bp.name} ({bp.unit})
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label={`Количество${selectedProduct ? ` (${selectedProduct.unit})` : ""}`}
            type="number"
            inputProps={{ min: 0, step: "0.01" }}
            value={form.qty}
            onChange={(e) => setForm((prev) => ({ ...prev, qty: e.target.value }))}
            autoFocus
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleDialogClose}>Отмена</Button>
          <Button onClick={handleDialogSave} variant="contained" disabled={saving}>
            Сохранить
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={Boolean(snackbar?.open)}
        autoHideDuration={5000}
        onClose={handleSnackbarClose}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        {snackbar && (
          <Alert onClose={handleSnackbarClose} severity={snackbar.severity} sx={{ width: "100%" }}>
            {snackbar.message}
          </Alert>
        )}
      </Snackbar>
    </Stack>
  );
}
