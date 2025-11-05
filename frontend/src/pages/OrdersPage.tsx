import CheckIcon from "@mui/icons-material/CheckCircleOutline";
import CloseIcon from "@mui/icons-material/Cancel";
import DownloadIcon from "@mui/icons-material/Download";
import ListIcon from "@mui/icons-material/List";
import RefreshIcon from "@mui/icons-material/Refresh";
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
import dayjs from "dayjs";
import { useCallback, useEffect, useMemo, useState } from "react";
import { apiClient } from "../api/client";
import { IngredientAggregate, Order } from "../types";

interface IngredientDialogState {
  orderId: number | null;
  ingredients: IngredientAggregate[];
}

type SortField = "id" | "date";
type SortDirection = "asc" | "desc";

const statusLabels: Record<string, string> = {
  CREATED: "Создан",
  CONFIRMED: "Подтверждён",
  CANCELLED: "Отменён"
};

export function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filterDate, setFilterDate] = useState<string>(() => dayjs().format("YYYY-MM-DD"));
  const [searchTerm, setSearchTerm] = useState("");
  const [sortField, setSortField] = useState<SortField>("date");
  const [sortDirection, setSortDirection] = useState<SortDirection>("desc");
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);

  const [ingredientsDialog, setIngredientsDialog] = useState<IngredientDialogState>({ orderId: null, ingredients: [] });
  const [ingredientsLoading, setIngredientsLoading] = useState(false);
  const [actionProcessing, setActionProcessing] = useState<number | null>(null);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: AlertColor }>({
    open: false,
    message: "",
    severity: "success"
  });
  const [exporting, setExporting] = useState(false);

  const loadOrders = useCallback(async () => {
    try {
      setLoading(true);
      const { data } = await apiClient.get<Order[]>("/orders");
      setOrders(data);
      setError(null);
    } catch (err) {
      setError("Не удалось загрузить заказы.");
      showSnackbar("Не удалось загрузить заказы.", "error");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  useEffect(() => {
    setPage(0);
  }, [searchTerm, filterDate]);

  const filteredOrders = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    const base = filterDate ? orders.filter((order) => order.targetDate === filterDate) : orders.slice();
    const filtered = term
      ? base.filter((order) => {
          const combined = `${order.id} ${order.userId} ${order.status ?? ""} ${order.comment ?? ""}`.toLowerCase();
          return combined.includes(term);
        })
      : base;
    filtered.sort((a, b) => {
      const dir = sortDirection === "asc" ? 1 : -1;
      if (sortField === "id") {
        return (a.id - b.id) * dir;
      }
      return a.targetDate.localeCompare(b.targetDate) * dir;
    });
    return filtered;
  }, [orders, filterDate, searchTerm, sortField, sortDirection]);

  const paginatedOrders = useMemo(() => {
    const start = page * rowsPerPage;
    return filteredOrders.slice(start, start + rowsPerPage);
  }, [filteredOrders, page, rowsPerPage]);

  const openIngredientsDialog = async (orderId: number) => {
    setIngredientsDialog({ orderId, ingredients: [] });
    setIngredientsLoading(true);
    try {
      const { data } = await apiClient.get<IngredientAggregate[]>(`/orders/${orderId}/aggregate`, {
        params: { client: false }
      });
      setIngredientsDialog({ orderId, ingredients: data });
    } catch (err) {
      setError("Не удалось загрузить ингредиенты.");
      showSnackbar("Ошибка загрузки ингредиентов.", "error");
    } finally {
      setIngredientsLoading(false);
    }
  };

  const closeIngredientsDialog = () => {
    setIngredientsDialog({ orderId: null, ingredients: [] });
  };

  const handleStatusChange = async (orderId: number, action: "confirm" | "cancel") => {
    if (!window.confirm(action === "confirm" ? "Подтвердить заказ?" : "Отменить заказ?")) {
      return;
    }
    setActionProcessing(orderId);
    try {
      await apiClient.patch(`/orders/${orderId}/${action}`);
      await loadOrders();
      showSnackbar(action === "confirm" ? "Заказ подтверждён." : "Заказ отменён.", "success");
    } catch (err) {
      setError("Не удалось обновить статус заказа.");
      showSnackbar("Ошибка обновления статуса.", "error");
    } finally {
      setActionProcessing(null);
    }
  };

  const handleExport = async () => {
    if (!filterDate) {
      showSnackbar("Укажите дату для экспорта.", "warning");
      return;
    }
    setExporting(true);
    try {
      const response = await apiClient.get(`/orders/${filterDate}/export`, { responseType: "blob" });
      const blob = new Blob([response.data], {
        type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `ingredients-${filterDate}.xlsx`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      showSnackbar("Экспорт выполнен.", "success");
    } catch (err) {
      showSnackbar("Ошибка экспорта.", "error");
    } finally {
      setExporting(false);
    }
  };

  const showSnackbar = (message: string, severity: AlertColor) => {
    setSnackbar({ open: true, message, severity });
  };

  const handleSnackbarClose = () => setSnackbar((prev) => ({ ...prev, open: false }));

  const handleSort = (field: SortField) => {
    setSortField((prevField) => {
      if (prevField === field) {
        setSortDirection((prevDirection) => (prevDirection === "asc" ? "desc" : "asc"));
        return prevField;
      }
      setSortDirection(field === "date" ? "desc" : "asc");
      return field;
    });
  };

  const handlePageChange = (_: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleRowsPerPageChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  return (
    <Stack spacing={3}>
      <Box display="flex" alignItems="center" justifyContent="space-between" flexWrap="wrap" gap={2}>
        <Typography variant="h4">Заказы</Typography>
        <Stack direction="row" spacing={2} alignItems="center">
          <TextField
            label="Поиск"
            size="small"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="ID, клиент, статус"
          />
          <TextField
            label="Дата"
            type="date"
            size="small"
            value={filterDate}
            onChange={(e) => setFilterDate(e.target.value)}
            InputLabelProps={{ shrink: true }}
          />
          <Button variant="outlined" startIcon={<RefreshIcon />} onClick={loadOrders} disabled={loading}>
            Обновить
          </Button>
          <Button
            variant="contained"
            color="secondary"
            startIcon={<DownloadIcon />}
            onClick={handleExport}
            disabled={exporting}
          >
            Экспорт Excel
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
                <TableCell sortDirection={sortField === "id" ? sortDirection : false}>
                  <TableSortLabel
                    active={sortField === "id"}
                    direction={sortField === "id" ? sortDirection : "asc"}
                    onClick={() => handleSort("id")}
                  >
                    ID
                  </TableSortLabel>
                </TableCell>
                <TableCell>Клиент</TableCell>
                <TableCell sortDirection={sortField === "date" ? sortDirection : false}>
                  <TableSortLabel
                    active={sortField === "date"}
                    direction={sortField === "date" ? sortDirection : "desc"}
                    onClick={() => handleSort("date")}
                  >
                    Дата
                  </TableSortLabel>
                </TableCell>
                <TableCell>Статус</TableCell>
                <TableCell>Комментарий</TableCell>
                <TableCell align="right">Действия</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={6} align="center">
                    <CircularProgress size={24} />
                  </TableCell>
                </TableRow>
              ) : paginatedOrders.length ? (
                paginatedOrders.map((order) => (
                  <TableRow key={order.id}>
                    <TableCell>{order.id}</TableCell>
                    <TableCell>{order.userId}</TableCell>
                    <TableCell>{order.targetDate}</TableCell>
                    <TableCell>{statusLabels[order.status ?? ""] ?? order.status ?? "—"}</TableCell>
                    <TableCell>{order.comment ?? "—"}</TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1}>
                        <Button
                          variant="outlined"
                          size="small"
                          startIcon={<ListIcon />}
                          onClick={() => openIngredientsDialog(order.id)}
                        >
                          Ингредиенты
                        </Button>
                        <IconButton
                          color="success"
                          size="small"
                          onClick={() => handleStatusChange(order.id, "confirm")}
                          disabled={order.status === "CONFIRMED" || actionProcessing === order.id}
                          aria-label="confirm"
                        >
                          <CheckIcon />
                        </IconButton>
                        <IconButton
                          color="error"
                          size="small"
                          onClick={() => handleStatusChange(order.id, "cancel")}
                          disabled={order.status === "CANCELLED" || actionProcessing === order.id}
                          aria-label="cancel"
                        >
                          <CloseIcon />
                        </IconButton>
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={6} align="center">
                    Заказов нет.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div"
          count={filteredOrders.length}
          page={page}
          onPageChange={handlePageChange}
          rowsPerPage={rowsPerPage}
          onRowsPerPageChange={handleRowsPerPageChange}
          rowsPerPageOptions={[10, 25, 50]}
        />
      </Paper>

      <Dialog open={ingredientsDialog.orderId != null} onClose={closeIngredientsDialog} maxWidth="sm" fullWidth>
        <DialogTitle>Ингредиенты заказа №{ingredientsDialog.orderId}</DialogTitle>
        <DialogContent dividers>
          {ingredientsLoading ? (
            <Box display="flex" justifyContent="center" py={2}>
              <CircularProgress size={24} />
            </Box>
          ) : ingredientsDialog.ingredients.length ? (
            <Stack spacing={1}>
              {ingredientsDialog.ingredients.map((item, idx) => (
                <Typography key={idx}>
                  — {item.name} — {item.totalQty}
                  {item.unit?.shortName ? ` ${item.unit.shortName}` : ""}
                </Typography>
              ))}
            </Stack>
          ) : (
            <Typography>Список пуст.</Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={closeIngredientsDialog}>Закрыть</Button>
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
