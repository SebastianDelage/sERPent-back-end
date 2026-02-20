package com.serpent.serpent.transactions.domain;



import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import com.serpent.serpent.catalog.domain.suppliers;


@Entity
@Table(name = "expenses")
public class expenses {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long expenses_id;
    @NotNull
    private Double recepit_numbrer; //No recuerdo que es este dato
    @NotNull
    private Boolean reimubursable;
    @ManyToOne
    @JoinColumn(name ="transaction_id",nullable = false)
    private  transactions transaction;
    @ManyToOne
    @JoinColumn(name = "supplier_id",nullable = false)
    private suppliers suppliers;
    @ManyToOne
    @JoinColumn(name = "expense_category_id",nullable = false)
    private ExpenseCategory expenseCategory;

    public expenses(){}

    public Long getExpenses_id() {
        return expenses_id;
    }

    public void setExpenses_id(Long expenses_id) {
        this.expenses_id = expenses_id;
    }

    public Double getRecepit_numbrer() {
        return recepit_numbrer;
    }

    public void setRecepit_numbrer(Double recepit_numbrer) {
        this.recepit_numbrer = recepit_numbrer;
    }

    public Boolean getReimubursable() {
        return reimubursable;
    }

    public void setReimubursable(Boolean reimubursable) {
        this.reimubursable = reimubursable;
    }

    public transactions getTranscation() {
        return transaction;
    }

    public void setTranscation(transactions transcation) {
        this.transactions = transaction;
    }

    public suppliers getSuppliers() {
        return suppliers;
    }

    public void setSuppliers(suppliers suppliers) {
        this.suppliers = suppliers;
    }

    public ExpenseCcategory getExpenseCategory() {
        return expenseCategory;
    }

    public void setExpenseCategory(ExpenseCcategory expenseCategory) {
        this.expenseCategory = expenseCategory;
    }
}
