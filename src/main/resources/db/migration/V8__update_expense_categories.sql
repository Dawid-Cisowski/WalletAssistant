-- Remap old expense categories to new ones
UPDATE expense_projections SET category = 'GROCERIES'     WHERE category = 'FOOD_AND_DRINKS';
UPDATE expense_projections SET category = 'TRANSPORT'     WHERE category = 'TRAVEL';
UPDATE expense_projections SET category = 'HOME_SUPPLIES' WHERE category = 'HOUSING';
UPDATE expense_projections SET category = 'HOME_SUPPLIES' WHERE category = 'UTILITIES';
UPDATE expense_projections SET category = 'OTHER'         WHERE category = 'SHOPPING';
UPDATE expense_projections SET category = 'OTHER'         WHERE category = 'BUSINESS';
UPDATE expense_projections SET category = 'OTHER'         WHERE category = 'SAVINGS_TRANSFER';
