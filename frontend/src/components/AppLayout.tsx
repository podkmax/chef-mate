import MenuIcon from "@mui/icons-material/RestaurantMenu";
import LogoutIcon from "@mui/icons-material/Logout";
import AssignmentIcon from "@mui/icons-material/Assignment";
import PeopleIcon from "@mui/icons-material/PeopleAlt";
import {
  AppBar,
  Box,
  Button,
  Container,
  IconButton,
  Toolbar,
  Typography
} from "@mui/material";
import { NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

interface AppLayoutProps {
  children: React.ReactNode;
}

const linkStyle: React.CSSProperties = {
  color: "inherit",
  textDecoration: "none",
  marginRight: "16px",
  display: "inline-flex",
  alignItems: "center",
  gap: "4px",
  fontWeight: 500
};

export function AppLayout({ children }: AppLayoutProps) {
  const { logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  return (
    <Box sx={{ minHeight: "100vh", backgroundColor: "#f5f5f5" }}>
      <AppBar position="static" sx={{ mb: 3 }}>
        <Toolbar>
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            ChefMate — Панель повара
          </Typography>
          <NavLink to="/menu" style={({ isActive }) => ({ ...linkStyle, opacity: isActive ? 1 : 0.8 })}>
            <MenuIcon fontSize="small" /> Меню
          </NavLink>
          <NavLink to="/orders" style={({ isActive }) => ({ ...linkStyle, opacity: isActive ? 1 : 0.8 })}>
            <AssignmentIcon fontSize="small" /> Заказы
          </NavLink>
          <NavLink to="/clients" style={({ isActive }) => ({ ...linkStyle, opacity: isActive ? 1 : 0.8 })}>
            <PeopleIcon fontSize="small" /> Клиенты
          </NavLink>
          <IconButton color="inherit" onClick={handleLogout} aria-label="logout">
            <LogoutIcon />
          </IconButton>
        </Toolbar>
      </AppBar>
      <Container maxWidth="lg" sx={{ pb: 4 }}>
        {children}
      </Container>
    </Box>
  );
}
