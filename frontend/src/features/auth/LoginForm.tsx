import { useState } from "react";
import { Input } from "../../shared/ui/Input";
import { Button } from "../../shared/ui/Button";
import { Alert } from "../../shared/ui/Alert";

interface LoginFormProps {
    onSubmit: (email: string, password: string) => void;
    loading?: boolean;
    error?: string;
}

export function LoginForm({ onSubmit, loading, error }: LoginFormProps) {
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        onSubmit(email, password);
    };

    return (
        <form onSubmit={handleSubmit} className="space-y-4">
            <Input
                label="Email"
                type="email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                required
            />
            <Input
                label="Password"
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
            />
            {error && <Alert tone="error" message={error} />}
            <Button type="submit" fullWidth disabled={loading}>
                {loading ? "Logging in..." : "Login"}
            </Button>
        </form>
    );
}
