import { Navigate, useLocation } from "react-router-dom";
import { useIsCook } from "../auth/AuthContext";

export function RequireCook({ children }: { children: JSX.Element }) {
  const isCook = useIsCook();
  const location = useLocation();

  if (!isCook) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return children;
}
