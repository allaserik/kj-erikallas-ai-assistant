import React from "react";

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
    children: React.ReactNode;
    fullWidth?: boolean;
    variant?: "default" | "outline";
}

export function Button({ children, fullWidth, variant = "default", className = "", ...props }: ButtonProps) {
    const base = "py-2 px-4 rounded font-semibold transition disabled:opacity-50 disabled:cursor-not-allowed";
    const styles =
        variant === "outline"
            ? "border border-green-700 text-green-700 hover:bg-green-50"
            : "bg-green-700 text-white hover:bg-green-800";
    return (
        <button
            className={`${base} ${styles} ${fullWidth ? "w-full" : ""} ${className}`}
            {...props}
        >
            {children}
        </button>
    );
}
