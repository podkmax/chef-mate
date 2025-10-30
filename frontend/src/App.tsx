import { CssBaseline } from "@mui/material";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { AppLayout } from "./components/AppLayout";
import { RequireCook } from "./components/RequireCook";
import { LoginPage } from "./pages/LoginPage";
import { MenuPage } from "./pages/MenuPage";
import { OrdersPage } from "./pages/OrdersPage";
import { ClientStocksPage } from "./pages/ClientStocksPage";

function ProtectedView({ children }: { children: JSX.Element }) {
  return (
    <RequireCook>
      <AppLayout>{children}</AppLayout>
    </RequireCook>
  );
}

export function App() {
  return (
    <AuthProvider>
      <CssBaseline />
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/menu"
            element={
              <ProtectedView>
                <MenuPage />
              </ProtectedView>
            }
          />
          <Route
            path="/orders"
            element={
              <ProtectedView>
                <OrdersPage />
              </ProtectedView>
            }
          />
          <Route
            path="/clients"
            element={
              <ProtectedView>
                <ClientStocksPage />
              </ProtectedView>
            }
          />
          <Route path="/" element={<Navigate to="/menu" replace />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
