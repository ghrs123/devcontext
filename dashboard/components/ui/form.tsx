import * as React from 'react';
import {
  Controller,
  type ControllerProps,
  FormProvider,
  useFormContext,
  type FieldPath,
  type FieldValues
} from 'react-hook-form';

import { Label } from '@/components/ui/label';
import { cn } from '@/lib/utils';
import { useT } from '@/lib/i18n/I18nProvider';
import type { TranslationKey } from '@/lib/i18n/dictionaries';

const Form = FormProvider;

type FormFieldContextValue<TFieldValues extends FieldValues = FieldValues, TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>> = {
  name: TName;
};

const FormFieldContext = React.createContext<FormFieldContextValue>({} as FormFieldContextValue);

const FormField = <
  TFieldValues extends FieldValues = FieldValues,
  TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>
>({
  ...props
}: ControllerProps<TFieldValues, TName>) => {
  return (
    <FormFieldContext.Provider value={{ name: props.name }}>
      <Controller {...props} />
    </FormFieldContext.Provider>
  );
};

const FormItem = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(({ className, ...props }, ref) => (
  <div ref={ref} className={cn('space-y-2', className)} {...props} />
));
FormItem.displayName = 'FormItem';

const FormLabel = React.forwardRef<HTMLLabelElement, React.ComponentPropsWithoutRef<typeof Label>>(
  ({ className, ...props }, ref) => {
    const { formState } = useFormContext();
    const fieldContext = React.useContext(FormFieldContext);
    const error = fieldContext.name ? formState.errors[fieldContext.name] : undefined;

    return <Label ref={ref} className={cn(error && 'text-danger', className)} {...props} />;
  }
);
FormLabel.displayName = 'FormLabel';

const FormControl = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(({ ...props }, ref) => (
  <div ref={ref} {...props} />
));
FormControl.displayName = 'FormControl';

const FormMessage = React.forwardRef<HTMLParagraphElement, React.HTMLAttributes<HTMLParagraphElement>>(
  ({ className, children, ...props }, ref) => {
    const t = useT();
    const { getFieldState, formState } = useFormContext();
    const fieldContext = React.useContext(FormFieldContext);
    const fieldState = fieldContext.name ? getFieldState(fieldContext.name, formState) : undefined;
    const raw = fieldState?.error?.message ?? children;

    if (!raw) {
      return null;
    }

    // Validation messages are stored as translation keys; t() returns the key
    // unchanged when it isn't one, so plain strings pass through.
    const body = typeof raw === 'string' ? t(raw as TranslationKey) : String(raw);

    return (
      <p ref={ref} className={cn('text-sm font-medium text-danger', className)} {...props}>
        {body}
      </p>
    );
  }
);
FormMessage.displayName = 'FormMessage';

const FormDescription = React.forwardRef<HTMLParagraphElement, React.HTMLAttributes<HTMLParagraphElement>>(
  ({ className, ...props }, ref) => <p ref={ref} className={cn('text-sm text-muted-foreground', className)} {...props} />
);
FormDescription.displayName = 'FormDescription';

export { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage };
