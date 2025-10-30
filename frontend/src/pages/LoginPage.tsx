import LoginIcon from "@mui/icons-material/Login";
import { Box, Button, Card, CardContent, Typography } from "@mui/material";
import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth, useIsCook } from "../auth/AuthContext";

export function LoginPage() {
  const { loginAsCook } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const isCook = useIsCook();

  useEffect(() => {
    if (isCook) {
      const redirectTo = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? "/menu";
      navigate(redirectTo, { replace: true });
    }
  }, [isCook, navigate, location.state]);

  const handleLogin = () => {
    loginAsCook();
  };

  return (
    <Box sx={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: "80vh" }}>
      <Card sx={{ maxWidth: 400 }}>
        <CardContent sx={{ textAlign: "center", display: "flex", flexDirection: "column", gap: 3 }}>
          <Typography variant="h5">Войти как повар</Typography>
          <Typography variant="body1" color="text.secondary">
            Для доступа к панели управления необходимо войти как повар.
          </Typography>
          <Button variant="contained" startIcon={<LoginIcon />} onClick={handleLogin}>
            Войти как повар
          </Button>
        </CardContent>
      </Card>
    </Box>
  );
}
